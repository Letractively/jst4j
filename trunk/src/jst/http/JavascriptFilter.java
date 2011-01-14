package jst.http;

import jst.ScriptRuntime;
import jst.TemplateException;
import org.apache.log4j.Logger;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.Enumeration;

import jst.TemplateContext;
import jst.FileTemplateLoader;

public class JavascriptFilter implements Filter {

    private static final Logger logger = Logger.getLogger( JavascriptFilter.class );

    ServletContext servletContext;
    TemplateContext templateContext;

    public JavascriptFilter() {
    }

    public void init(FilterConfig filterConfig) throws ServletException {
        try {
            logger.info("Initializing serverside javascript templates...");
            String sanitizer = filterConfig.getInitParameter("sanitizer");
            String prod = filterConfig.getInitParameter("production");
            String scriptLocation = filterConfig.getInitParameter("scriptLocation");

            servletContext = filterConfig.getServletContext();

            templateContext = new TemplateContext();
            templateContext.setSanitizingFunction( sanitizer != null ? sanitizer : "Html.html");
            templateContext.addLoader( new ServletTemplateLoader( filterConfig.getServletContext(), scriptLocation ) );
            if( prod != null ) {
                templateContext.setProduction( Boolean.parseBoolean(prod) );
            }
            
            templateContext.include("core/html.js");

            String templatePaths = filterConfig.getInitParameter("template.paths");
            if( templatePaths != null ) {
                String[] paths = templatePaths.split(",");
                for( String path : paths ) {
                    File filepath = new File( path );
                    if( filepath.exists() ) {
                        templateContext.addLoader( new FileTemplateLoader( filepath ) );
                    } else {
                        logger.warn( path + " does not exist!  Not being added to the loader list." );
                    }
                }
            }
        } catch (IOException e) {
            throw new ServletException( "Failed to load the default script.", e );
        }
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        try {
            logger.debug("Forward to " + ((HttpServletRequest)servletRequest).getPathInfo() );

            String scriptName = servletRequest.getAttribute(TemplateDispatcher.JST_SCRIPT ).toString();
            //String scriptName = ((HttpServletRequest)servletRequest).getRequestURI();

            ScriptRuntime runtime = initializeScript( scriptName, (HttpServletRequest)servletRequest, (HttpServletResponse)servletResponse );

            writeResponse( servletResponse, runtime.invoke() );
        } catch( TemplateException ex ) {
            writeScriptError( (HttpServletRequest)servletRequest, (HttpServletResponse)servletResponse, ex );
        }
    }

    private void writeScriptError(HttpServletRequest request, HttpServletResponse response, TemplateException ex) throws IOException {
        ScriptRuntime runtime = templateContext.load( "templates/exception.jst");
        runtime.addGlobalVariable( "request", request );
        runtime.addGlobalVariable( "response", response );
        runtime.addGlobalVariable( "servletContext", servletContext );
        runtime.addVariable("ex", ex );
        writeResponse( response, runtime.invoke() );
    }

    private ScriptRuntime initializeScript(String scriptName, HttpServletRequest request, HttpServletResponse response) throws IOException {
        ScriptRuntime runtime = templateContext.load( scriptName );

        Enumeration attributes = request.getAttributeNames();
        while( attributes.hasMoreElements() ) {
            String name = (String) attributes.nextElement();
            if( name.startsWith( TemplateDispatcher.JST_MIXIN ) ) {
                String mixinName = name.substring( TemplateDispatcher.JST_MIXIN.length() );
                runtime.mixin( mixinName, request.getAttribute( name ) );
            } else if( name.equalsIgnoreCase( TemplateDispatcher.JST_LAYOUT ) ) {
                runtime.setLayout( request.getAttribute( name ).toString() );
            } else if( name.startsWith(TemplateDispatcher.JST_VARIABLE) ) {
                String varName = name.substring( TemplateDispatcher.JST_VARIABLE.length() );
                runtime.addVariable( varName, request.getAttribute( name ) );
            } else if( name.startsWith( TemplateDispatcher.JST_SCRIPT_MIXIN ) ) {
                runtime.include( request.getAttribute( name ).toString() );
            }
        }

        runtime.addGlobalVariable( "request", request );
        runtime.addGlobalVariable( "response", response );
        runtime.addGlobalVariable( "servletContext", servletContext );

        return runtime;
    }

    private void writeResponse(ServletResponse servletResponse, Object result) throws IOException {
        PrintWriter writer = servletResponse.getWriter();
        try {
            writer.print( result.toString() );
        } finally {
            writer.flush();
            writer.close();
        }
    }

    public void destroy() {

    }

    public static void main(String[] args) {
        System.out.println( String.format("Template.%1$s = function() { return this.__delegate[\"%1$s\"].apply( this.__delegate, arguments ); }", "fizzle" ) );
    }
}