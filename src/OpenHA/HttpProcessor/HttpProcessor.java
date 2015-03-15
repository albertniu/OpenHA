package OpenHA.HttpProcessor;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import OpenHA.HAProcessor.HAProcessor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class httpServerDaemon
{
	void startHttpServer()
	{
		try
		{
			InetSocketAddress addr = new InetSocketAddress ( Integer.parseInt ( HAProcessor.LOCAL_PORT ) );
			HttpServer server = HttpServer.create ( addr, 0 );

			server.createContext ( "/", new httpHandler () );
			server.setExecutor ( Executors.newFixedThreadPool ( 1 ) );
			server.start ();

			HAProcessor.m_logger.info ( "Http Processor is listening on " + HAProcessor.LOCAL_PORT );
		}
		catch ( Exception ex )
		{
			HAProcessor.m_logger.error ( ex.toString () );
		}
	}
}

class httpHandler implements HttpHandler
{
	public void handle(HttpExchange exchange)
	{
		HAProcessor.m_logger.debug ( "HttpHandler Start" );

		try
		{
			if ( HAProcessor.m_HAState.equals ( "master" ) )
				exchange.sendResponseHeaders ( 200, 0 );
			else
				exchange.sendResponseHeaders ( 403, 0 );

			HAProcessor.m_logger.debug ( "HttpHandler End" );
		}
		catch ( Exception ex )
		{
			HAProcessor.m_logger.error ( ex.toString () );
		}
		finally
		{
			try
			{
				exchange.close ();
				exchange = null;
			}
			catch ( Exception ex )
			{
				HAProcessor.m_logger.error ( ex.toString () );
			}
		}
	}
}

public class HttpProcessor
{
	static public void Start()
	{
		try
		{
			httpServerDaemon httpserver = new httpServerDaemon ();
			httpserver.startHttpServer ();
		}
		catch ( Exception ex )
		{
			HAProcessor.m_logger.error ( ex.toString () );
		}
	}
}
