package org.fusesource.scalate.servlet

import scala.xml.Node
import javax.servlet.http._
import org.fusesource.scalate.util.{Lazy, XmlEscape}
import java.text.{DateFormat, NumberFormat}
import java.io._
import javax.servlet.{ServletOutputStream, ServletContext, RequestDispatcher, ServletException}
import java.lang.String
import collection.mutable.{Stack, ListBuffer, HashMap}
import org.fusesource.scalate.{NoSuchTemplateException, TemplateContext, NoSuchAttributeException}
import java.util.{Properties, Date, Locale}

/**
 * A template context for use in servlets
 *
 * @version $Revision: 1.1 $
 */
case class ServletTemplateContext(var out: PrintWriter, request: HttpServletRequest, response: HttpServletResponse, servletContext: ServletContext) extends TemplateContext() {

  /**
   * Returns the attribute of the given type or a   { @link NoSuchAttributeException } exception is thrown
   */
  def attribute[T](name: String): T = {
    val value = request.getAttribute(name)
    if (value != null) {
      value.asInstanceOf[T]
    }
    else {
      throw new NoSuchAttributeException(name)
    }
  }

  /**
   * Returns the attribute of the given name and type or the default value if it is not available
   */
  def attributeOrElse[T](name: String, defaultValue: T): T = {
    val value = request.getAttribute(name)
    if (value != null) {
      value.asInstanceOf[T]
    }
    else {
      defaultValue
    }
  }

  /**
   * Updates the named attribute with the given value
   */
  def setAttribute[T](name: String, value: T): Unit = {
    request.setAttribute(name, value)
  }


  override def locale: Locale = {
    var locale = request.getLocale
    if (locale == null) {
      Locale.getDefault
    }
    else {
      locale
    }
  }



  /**
   * Renders the view of the given model object, looking for the view in
   * packageName/className.viewName.ssp
   */
  def render(model: AnyRef, view: String = "index"): Unit = {
    if (model == null) {
      throw new NullPointerException("No model object given!")
    }
    var flag = true
    var aClass = model.getClass
    while (flag && aClass != null && aClass != classOf[Object]) {

      resolveViewForType(model, view, aClass) match {
        case Some(dispatcher) =>
          flag = false
          doInclude(dispatcher, model)

        case _ => aClass = aClass.getSuperclass
      }
    }

    if (flag) {
      aClass = model.getClass
      val interfaceList = new ListBuffer[Class[_]]()
      while (aClass != null && aClass != classOf[Object]) {
        for (i <- aClass.getInterfaces) {
          if (!interfaceList.contains(i)) {
            interfaceList.append(i)
          }
        }
        aClass = aClass.getSuperclass
      }

      flag = true
      for (i <- interfaceList; if (flag)) {
        resolveViewForType(model, view, i) match {
          case Some(dispatcher) =>
            flag = false
            doInclude(dispatcher, model)

          case _ =>
        }
      }
    }

    if (flag) {
      throw new NoSuchTemplateException(model, view)
    }
  }

  protected def doInclude(dispatcher: RequestDispatcher, model: AnyRef = null): Unit = {
    out.flush

    val wrappedRequest = new RequestWrapper(request)
    val wrappedResponse = new ResponseWrapper(response)
    if (model != null) {
      wrappedRequest.setAttribute("it", model)
    }

    dispatcher.forward(wrappedRequest, wrappedResponse)
    val text = wrappedResponse.getString
    out.write(text)
    out.flush()
  }

  protected def resolveViewForType(model: AnyRef, view: String, aClass: Class[_]): Option[RequestDispatcher] = {
    for (prefix <- viewPrefixes; postfix <- viewPostfixes) {
      val path = aClass.getName.replace('.', '/') + "." + view + postfix
      val fullPath = if (prefix.isEmpty) {"/" + path} else {"/" + prefix + "/" + path}

      val url = servletContext.getResource(fullPath)
      if (url != null) {
        val dispatcher = request.getRequestDispatcher(fullPath)
        if (dispatcher != null) {
          return Some(dispatcher)
          //return Some(new RequestDispatcherWrapper(dispatcher, fullPath, hc, model))
        }
      }
    }
    None
  }

  /**
   * Includes the given page inside this page
   */
  def include(page: String): Unit = {
    val dispatcher = getRequestDispatcher(page)
    doInclude(dispatcher)
  }

  /**
   * Forwards this request to the given page
   */
  def forward(page: String): Unit = {
    getRequestDispatcher(page).forward(request, response)
  }

  private def getRequestDispatcher(path: String) = {
    val dispatcher = request.getRequestDispatcher(path)
    if (dispatcher == null) {
      throw new ServletException("No dispatcher available for path: " + path)
    }
    else {
      dispatcher
    }
  }

  class RequestWrapper(request: HttpServletRequest) extends HttpServletRequestWrapper(request) {
    override def getMethod() = {
      "GET";
    }

    val _attributes = new HashMap[String, Object]

    override def getAttributeNames:java.util.Enumeration[Object] = {
      val rc = new Properties
      val names = super.getAttributeNames
      while ( names.hasMoreElements ) {
        val name = names.nextElement.asInstanceOf[String]
        rc.put(name, "");
      }
      _attributes.foreach(e=>{
        rc.put(e._1, "");
      })
      rc.keys.asInstanceOf[java.util.Enumeration[Object]]
    }

    override def setAttribute(name: String, value: Object) = _attributes(name) = value

    override def getAttribute(name: String) = _attributes.get(name).getOrElse(super.getAttribute(name))
  }

  class ResponseWrapper(response: HttpServletResponse, charEncoding: String = null) extends HttpServletResponseWrapper(response) {
    val sw = new StringWriter()
    val bos = new ByteArrayOutputStream()
    val sos = new ServletOutputStream() {
      def write(b: Int): Unit = {
        bos.write(b)
      }
    }
    var isWriterUsed = false
    var isStreamUsed = false
    var _status = 200


    override def getWriter(): PrintWriter = {
      if (isStreamUsed)
        throw new IllegalStateException("Attempt to import illegal Writer")
      isWriterUsed = true
      new PrintWriter(sw)
    }

    override def getOutputStream(): ServletOutputStream = {
      if (isWriterUsed)
        throw new IllegalStateException("Attempt to import illegal OutputStream")
      isStreamUsed = true
      sos
    }

    override def reset = {}

    override def resetBuffer = {}

    override def setContentType(x: String) = {} // ignore

    override def setLocale(x: Locale) = {} // ignore

    override def setStatus(status: Int): Unit = _status = status

    def getStatus() = _status

    def getString() = {
      if (isWriterUsed)
        sw.toString()
      else if (isStreamUsed) {
        if (charEncoding != null && !charEncoding.equals(""))
          bos.toString(charEncoding)
        else
          bos.toString(defaultCharacterEncoding)
      } else
        "" // target didn't write anything
    }
  }
}