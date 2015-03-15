package OpenHA.HAProcessor;

import java.util.Calendar;
import java.util.Vector;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import OpenHA.CfgFileReader.CfgFileReader;
import OpenHA.HttpProcessor.HttpProcessor;

class ShutdownThread extends Thread
{
	public void run()
	{
		HAProcessor.m_logger.debug ( "HAProcessor Shutdown [...]" );
		HAProcessor.Stop ();
		HAProcessor.m_logger.debug ( "HAProcessor Shutdown [ OK ]" );
	}
}

class HAProcessorThread extends Thread
{
	private boolean m_pleaseWait = true;
	public long m_tmLastActive = Calendar.getInstance ().getTimeInMillis ();
	public long m_nLastStatus = 0;

	private boolean httpTest(String url)
	{
		HttpGet httpget = null;
		HttpResponse response = null;

		HttpClient client = new DefaultHttpClient ();
		client.getParams ().setIntParameter ( HttpConnectionParams.CONNECTION_TIMEOUT, HAProcessor.CONNECT_TIME_OUT );
		client.getParams ().setIntParameter ( HttpConnectionParams.SO_TIMEOUT, HAProcessor.SOCKET_TIME_OUT );

		try
		{
			httpget = new HttpGet ( url );
			response = client.execute ( httpget );
			int statusCode = response.getStatusLine ().getStatusCode ();

			if ( statusCode == 200 )
				return true;

			return false;
		}
		catch ( Exception ex )
		{
			HAProcessor.m_logger.error ( ex.toString (), ex );
			return false;
		}
		finally
		{
			if ( client != null )
			{
				client.getConnectionManager ().shutdown ();
				client = null;
			}

			httpget = null;
			response = null;
		}
	}

	public boolean pingHost(String host)
	{
		if ( host == null || host.equals ( "" ) )
			return false;

		Process proc = null;

		try
		{
			proc = Runtime.getRuntime ().exec ( HAProcessor.CMD_PING + " " + host );

			if ( proc.waitFor () == 0 )
			{
				HAProcessor.m_logger.debug ( host + " is reachable" );
				return true;
			}
		}
		catch ( Exception ex )
		{
			HAProcessor.m_logger.error ( ex.toString (), ex );
		}
		finally
		{
			if ( proc != null )
			{
				proc.destroy ();
				proc = null;
			}
		}

		HAProcessor.m_logger.debug ( host + " is not reachable" );
		return false;
	}

	public void heartbeat()
	{
		if ( HAProcessor.PEER_URL.equals ( "" ) )
		{
			HAProcessor.m_HAState = "master";
			HAProcessor.m_logger.debug ( "status=master" );
			return;
		}

		if ( pingHost ( HAProcessor.GATEWAY_IP ) == false && pingHost ( HAProcessor.LOCAL_HOST ) == false && pingHost ( HAProcessor.PEER_HOST ) == false )
		{
			HAProcessor.m_HAState = "slave";
			HAProcessor.m_logger.debug ( "status=slave" );
			return;
		}

		for ( int i = 0; i < HAProcessor.m_localProcessURLs.size (); ++i )
		{
			if ( httpTest ( HAProcessor.m_localProcessURLs.get ( i ) ) == false )
			{
				HAProcessor.m_HAState = "slave";
				HAProcessor.m_logger.debug ( "status=slave" );
				return;
			}
		}

		String nextStatus = HAProcessor.m_HAState;
		int changeCount = 0;

		for ( int i = 0; i < HAProcessor.CONNECT_COUNT; ++i )
		{
			HttpPost post = null;
			HttpResponse response = null;

			HttpClient client = new DefaultHttpClient ();
			client.getParams ().setIntParameter ( HttpConnectionParams.CONNECTION_TIMEOUT, HAProcessor.CONNECT_TIME_OUT );
			client.getParams ().setIntParameter ( HttpConnectionParams.SO_TIMEOUT, HAProcessor.SOCKET_TIME_OUT );

			try
			{
				post = new HttpPost ( HAProcessor.PEER_URL );
				response = client.execute ( post );
				int statusCode = response.getStatusLine ().getStatusCode ();

				if ( statusCode == 200 )
				{
					changeCount += 1;
					nextStatus = "slave";
				}
				else
				{
					changeCount += 1;
					nextStatus = "master";
				}
			}
			catch ( HttpHostConnectException ex )
			{
				HAProcessor.m_logger.error ( ex.toString (), ex );
				nextStatus = "master";
				changeCount += 1;
			}
			catch ( ConnectTimeoutException ex )
			{
				HAProcessor.m_logger.error ( ex.toString (), ex );
				nextStatus = "master";
				changeCount += 1;
			}
			catch ( Exception ex )
			{
				HAProcessor.m_logger.error ( ex.toString (), ex );
			}
			finally
			{
				if ( client != null )
				{
					client.getConnectionManager ().shutdown ();
					client = null;
				}

				post = null;
				response = null;
			}
		}

		if ( HAProcessor.DEFAULT_PRIORITY == 1 )
		{
			if ( HAProcessor.m_HAState.equals ( getPeerStateEx () ) == true )
			{
				HAProcessor.m_HAState = "master";
				HAProcessor.m_logger.debug ( "status=" + HAProcessor.m_HAState );
			}
			else if ( changeCount == HAProcessor.CONNECT_COUNT )
			{
				HAProcessor.m_HAState = nextStatus;
				HAProcessor.m_logger.debug ( "status=" + HAProcessor.m_HAState );
			}
		}
		else if ( HAProcessor.DEFAULT_PRIORITY == 0 )
		{
			if ( HAProcessor.m_HAState.equals ( getPeerStateEx () ) == true )
			{
				if ( getPeerState ().equals ( "active" ) )
				{
					HAProcessor.m_HAState = "slave";
					HAProcessor.m_logger.debug ( "status=" + HAProcessor.m_HAState );
				}
				else
				{
					HAProcessor.m_HAState = "master";
					HAProcessor.m_logger.debug ( "status=" + HAProcessor.m_HAState );
				}
			}
			else if ( changeCount == HAProcessor.CONNECT_COUNT )
			{
				HAProcessor.m_HAState = nextStatus;
				HAProcessor.m_logger.debug ( "status=" + HAProcessor.m_HAState );
			}
		}
		else if ( changeCount == HAProcessor.CONNECT_COUNT )
		{
			HAProcessor.m_HAState = nextStatus;
			HAProcessor.m_logger.debug ( "status=" + HAProcessor.m_HAState );
		}
	}

	private String getPeerStateEx()
	{
		if ( HAProcessor.PEER_URL.equals ( "" ) )
		{
			return "slave";
		}

		HttpPost post = null;
		HttpResponse response = null;

		HttpClient client = new DefaultHttpClient ();
		client.getParams ().setIntParameter ( HttpConnectionParams.CONNECTION_TIMEOUT, HAProcessor.CONNECT_TIME_OUT );
		client.getParams ().setIntParameter ( HttpConnectionParams.SO_TIMEOUT, HAProcessor.SOCKET_TIME_OUT );

		try
		{
			post = new HttpPost ( HAProcessor.PEER_URL );
			response = client.execute ( post );
			int statusCode = response.getStatusLine ().getStatusCode ();

			if ( statusCode == 200 )
				return "master";

			return "slave";
		}
		catch ( Exception ex )
		{
			HAProcessor.m_logger.error ( ex.toString (), ex );
			return "slave";
		}
		finally
		{
			if ( client != null )
			{
				client.getConnectionManager ().shutdown ();
				client = null;
			}

			post = null;
			response = null;
		}
	}

	public String getPeerState()
	{
		if ( HAProcessor.PEER_URL.equals ( "" ) )
		{
			return "inactive";
		}

		HttpPost post = null;
		HttpClient client = new DefaultHttpClient ();
		client.getParams ().setIntParameter ( HttpConnectionParams.CONNECTION_TIMEOUT, HAProcessor.CONNECT_TIME_OUT );
		client.getParams ().setIntParameter ( HttpConnectionParams.SO_TIMEOUT, HAProcessor.SOCKET_TIME_OUT );

		try
		{
			post = new HttpPost ( HAProcessor.PEER_URL );
			client.execute ( post );
			return "active";
		}
		catch ( Exception ex )
		{
			HAProcessor.m_logger.error ( ex.toString (), ex );
			return "inactive";
		}
		finally
		{
			if ( client != null )
			{
				client.getConnectionManager ().shutdown ();
				client = null;
			}

			post = null;
		}
	}

	public void run()
	{
		while ( true )
		{
			Process proc = null;

			try
			{
				String lastState = HAProcessor.m_HAState;
				heartbeat ();
				String nowState = HAProcessor.m_HAState;

				if ( !nowState.equals ( lastState ) )
				{
					if ( nowState.equals ( "master" ) )
					{
						if ( !HAProcessor.CMD_CHANGE_TO_MASTER.equals ( "" ) )
						{
							HAProcessor.m_logger.debug ( "exec " + HAProcessor.CMD_CHANGE_TO_MASTER );
							proc = Runtime.getRuntime ().exec ( HAProcessor.CMD_CHANGE_TO_MASTER );
							proc.waitFor ();
						}
					}
					else
					{
						if ( !HAProcessor.CMD_CHANGE_TO_SLAVE.equals ( "" ) )
						{
							HAProcessor.m_logger.debug ( "exec " + HAProcessor.CMD_CHANGE_TO_SLAVE );
							proc = Runtime.getRuntime ().exec ( HAProcessor.CMD_CHANGE_TO_SLAVE );
							proc.waitFor ();
						}
					}
				}

				m_nLastStatus = 1;
				m_tmLastActive = Calendar.getInstance ().getTimeInMillis ();

				m_pleaseWait = false;
				sleep ( (long) (HAProcessor.INTERVAL_HA * Math.random ()) );
			}
			catch ( Exception ex )
			{
				HAProcessor.m_logger.error ( ex.toString (), ex );
				m_nLastStatus = 0;
				m_tmLastActive = Calendar.getInstance ().getTimeInMillis ();
			}
			finally
			{
				if ( proc != null )
				{
					proc.destroy ();
					proc = null;
				}
			}
		}
	}

	public void waitFor()
	{
		try
		{
			while ( m_pleaseWait )
				sleep ( 10 );
		}
		catch ( Exception ex )
		{
			HAProcessor.m_logger.error ( ex.toString (), ex );
		}
	}
}

public class HAProcessor
{
	// static private String m_Version = "2012-12-15 03:10 Build V1.3.0, Albert Newton";
	// static private String m_Version = "2013-07-11 19:20 Build V1.5.0, Albert Newton";
	// static private String m_Version = "2013-08-28 22:15 Build V1.6.1, Albert Newton";
	// static private String m_Version = "2014-01-10 19:01 Build V1.7.0, Albert Newton";
	static private String m_Version = "2014-09-29 15:03 Build V1.8.0, Albert Newton";
	static public Logger m_logger;

	static public int INTERVAL_HA;
	static public int CONNECT_TIME_OUT;
	static public int SOCKET_TIME_OUT;
	static public int CONNECT_COUNT;
	static public int DEFAULT_PRIORITY;

	static public String CMD_PING;
	static public String CMD_CHANGE_TO_MASTER;
	static public String CMD_CHANGE_TO_SLAVE;
	static public String GATEWAY_IP;
	static public String LOCAL_URL;
	static public String LOCAL_HOST;
	static public String LOCAL_PORT;
	static public String PEER_URL;
	static public String PEER_HOST;
	static public Vector<String> m_localProcessURLs = new Vector<String> ();

	static public HAProcessorThread m_HAProcessorThread = null;
	static public String m_HAState = "slave";

	static public void LoadConfig()
	{
		CfgFileReader cfg = null;

		try
		{
			cfg = new CfgFileReader ( "openha.ini" );

			// [HA]
			INTERVAL_HA = cfg.getInteger ( "INTERVAL_HA", 1000 );
			CONNECT_TIME_OUT = cfg.getInteger ( "CONNECT_TIME_OUT", 1000 );
			SOCKET_TIME_OUT = cfg.getInteger ( "SOCKET_TIME_OUT", 1000 );
			CONNECT_COUNT = cfg.getInteger ( "CONNECT_COUNT", 3 );
			DEFAULT_PRIORITY = cfg.getInteger ( "DEFAULT_PRIORITY", -1 );

			CMD_PING = cfg.getString ( "CMD_PING", "ping -c 4" );
			CMD_CHANGE_TO_MASTER = cfg.getString ( "CMD_CHANGE_TO_MASTER", "" );
			CMD_CHANGE_TO_SLAVE = cfg.getString ( "CMD_CHANGE_TO_SLAVE", "" );
			GATEWAY_IP = cfg.getString ( "GATEWAY_IP", "" );
			LOCAL_URL = cfg.getString ( "LOCAL_URL", "" );
			PEER_URL = cfg.getString ( "PEER_URL", "" );

			if ( LOCAL_URL.endsWith ( "/" ) == false )
			{
				LOCAL_URL += "/";
			}

			if ( PEER_URL.endsWith ( "/" ) == false )
			{
				PEER_HOST += "/";
			}

			int start = LOCAL_URL.indexOf ( "://" ) + 3;
			int end = LOCAL_URL.lastIndexOf ( ":" );

			if ( start < end )
			{
				LOCAL_HOST = LOCAL_URL.substring ( start, end );
			}

			start = LOCAL_URL.lastIndexOf ( ":" ) + 1;
			end = LOCAL_URL.lastIndexOf ( "/" );

			if ( start < end )
			{
				LOCAL_PORT = LOCAL_URL.substring ( start, end );
			}

			start = PEER_URL.indexOf ( "://" ) + 3;
			end = PEER_URL.lastIndexOf ( ":" );

			if ( start < end )
			{
				PEER_HOST = PEER_URL.substring ( start, end );
			}

			for ( int i = 0; i < 10; ++i )
			{
				String key = String.format ( "LOCAL_PROCESS_URL%d", i + 1 );
				String url = cfg.getString ( key, "" );

				if ( url.length () > 0 )
				{
					m_localProcessURLs.add ( url );
				}
			}
		}
		catch ( Exception ex )
		{
			ex.printStackTrace ();
		}
		finally
		{
			if ( cfg != null )
			{
				cfg.close ();
				cfg = null;
			}
		}
	}

	static private void InitLog()
	{
		try
		{
			m_logger = Logger.getLogger ( HAProcessor.class.getName () );
			PropertyConfigurator.configure ( "log4j.properties" );
		}
		catch ( Exception ex )
		{
			ex.printStackTrace ();
		}
	}

	static public void Start()
	{
		try
		{
			InitLog ();
			LoadConfig ();
			Runtime.getRuntime ().addShutdownHook ( new ShutdownThread () );
		}
		catch ( Exception ex )
		{
			ex.printStackTrace ();
			System.exit ( -1 );
		}

		m_logger.info ( "Version=" + m_Version );

		try
		{
			// 避免启动过快，对方探测还没有结束。
			Thread.sleep ( CONNECT_TIME_OUT );

			HttpProcessor.Start ();
			m_HAProcessorThread = new HAProcessorThread ();
			m_HAProcessorThread.setPriority ( Thread.MIN_PRIORITY );
			m_HAProcessorThread.start ();
			m_HAProcessorThread.waitFor ();
		}
		catch ( Exception ex )
		{
			m_logger.error ( ex.toString (), ex );
			System.exit ( -1 );
		}

		m_logger.info ( "HAProcessor Startup  [ OK ]" );
	}

	static public void Stop()
	{
		Process proc = null;

		try
		{
			if ( !HAProcessor.CMD_CHANGE_TO_SLAVE.equals ( "" ) )
			{
				HAProcessor.m_logger.debug ( "exec " + HAProcessor.CMD_CHANGE_TO_SLAVE );
				proc = Runtime.getRuntime ().exec ( HAProcessor.CMD_CHANGE_TO_SLAVE );
				proc.waitFor ();
			}
		}
		catch ( Exception ex )
		{
			m_logger.error ( ex.toString (), ex );
		}
		finally
		{
			if ( proc != null )
			{
				proc.destroy ();
				proc = null;
			}
		}
	}

	static public String GetState()
	{
		return m_HAState;
	}

	static public String GetPeerState()
	{
		if ( m_HAProcessorThread == null )
			return "inactive";

		return m_HAProcessorThread.getPeerState ();
	}
}

class HAProcessorTest
{
	public static void main(String[] args)
	{
		HAProcessor.Start ();
	}
}
