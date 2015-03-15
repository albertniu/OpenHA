package OpenHA.CfgFileReader;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Properties;

import OpenHA.HAProcessor.HAProcessor;

public class CfgFileReader
{
	Properties pts = null;
	FileInputStream fs = null;
	InputStreamReader is = null;
	
	public CfgFileReader(String filename)
	{
		try
		{
			fs = new FileInputStream ( filename );
			is = new InputStreamReader ( fs, "utf-8" );
			pts = new Properties ();
			pts.load ( is );
		}
		catch ( Exception ex )
		{
			fs = null;
			pts = null;
			HAProcessor.m_logger.error ( ex.toString (), ex );
		}
	}

	public String getString(String key, String def)
	{
		if ( pts == null )
			return def;

		return pts.getProperty ( key, def );
	}

	public int getInteger(String key, int def)
	{
		if ( pts == null )
			return def;

		String tmp = pts.getProperty ( key );
		if ( tmp == null )
			return def;

		return Integer.parseInt ( tmp );
	}

	public void close()
	{
		try
		{
			if ( is != null )
				is.close ();
		}
		catch ( Exception ex )
		{
			HAProcessor.m_logger.error ( ex.toString (), ex );
		}
		
		try
		{
			if ( fs != null )
				fs.close ();
		}
		catch ( Exception ex )
		{
			HAProcessor.m_logger.error ( ex.toString (), ex );
		}
	}
}