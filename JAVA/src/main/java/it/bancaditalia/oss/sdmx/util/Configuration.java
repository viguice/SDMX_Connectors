/* Copyright 2010,2014 Bank Of Italy
*
* Licensed under the EUPL, Version 1.1 or - as soon they
* will be approved by the European Commission - subsequent
* versions of the EUPL (the "Licence");
* You may not use this work except in compliance with the
* Licence.
* You may obtain a copy of the Licence at:
*
*
* http://ec.europa.eu/idabc/eupl
*
* Unless required by applicable law or agreed to in
* writing, software distributed under the Licence is
* distributed on an "AS IS" basis,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
* express or implied.
* See the Licence for the specific language governing
* permissions and limitations under the Licence.
*/
package it.bancaditalia.oss.sdmx.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale.LanguageRange;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.Subject;
import javax.swing.JFrame;

/**
 * @author Attilio Mattiocco
 *
 */
public class Configuration
{

	// TODO: will be replaced by StandardCharsets#UTF_8 in Java 7
	public static final Charset UTF_8 = Charset.forName("UTF-8");
	public static final String SDMX_CODES_POLICY_ID = "code";
	public static final String SDMX_CODES_POLICY_DESC = "description";
	public static final String SDMX_CODES_POLICY_BOTH = "both";
	public static final String SDMX_CODES_POLICY_ATTRIBUTES = "attributes";

	protected static final String PROXY_AUTH_KERBEROS = "Kerberos";
	protected static final String PROXY_AUTH_DIGEST = "digest";
	protected static final String PROXY_AUTH_BASIC = "basic";
	protected static final String JAVA_SECURITY_KERBEROS_PROP = "java.security.krb5.conf";
	protected static final String JAVA_SECURITY_AUTH_LOGIN_CONFIG_PROP = "java.security.auth.login.config";
	protected static final String HTTP_AUTH_PREF_PROP = "http.auth.preference";
	protected static final String SSL_DISABLE_CERT_CHECK_PROP = "ssl.disable.cert.check";
	protected static final String SSL_TRUSTSTORE_PROP = "javax.net.ssl.trustStore";

	protected static final String GLOBAL_CONFIGURATION_FILE_PROP = "SDMX_CONF";
	protected static final String EXTERNAL_PROVIDERS_PROP = "external.providers";
	protected static final String PROXY_NAME_PROP = "http.proxy.name";
	protected static final String PROXY_DEFAULT_PROP = "http.proxy.default";
	protected static final String HTTP_AUTH_USER_PROP = "http.auth.user";
	protected static final String PROXY_AUTH_PW_PROP = "http.auth.pw";
	protected static final String REVERSE_DUMP_PROP = "reverse.dump";
	protected static final String SDMX_LANG_PROP = "sdmx.lang";
	protected static final String LATE_RESP_RETRIES_PROP = "late.response.retries";
	protected static final String TABLE_DUMP_PROP = "table.dump";
	protected static final String READ_TIMEOUT_PROP = "read.timeout";
	protected static final String CONNECT_TIMEOUT_PROP = "connect.timeout";
	protected static final Logger SDMX_LOGGER = Logger.getLogger("SDMX");
	protected static List<LanguageRange> SDMX_LANG = LanguageRange.parse("en");

	private static final String UIS_API_KEY_PROP = "uis.api.key";
	private static final String SDMX_CODES_POLICY = "handle.sdmx.codes";

	private static final String REVERSE_DUMP_DEFAULT = "FALSE";
	private static final String TABLE_DUMP_DEFAULT = "FALSE";
	private static final String SDMX_DEFAULT_LANG = "en";
	private static final String SDMX_DEFAULT_TIMEOUT = "0";
	private static final String DUMP_XML_PREFIX = "xml.dump.prefix";
	private static final String sourceClass = Configuration.class.getSimpleName();

	private static final String CONFIGURATION_FILE_NAME = "configuration.properties";
	private static Properties props = new Properties();
	private static boolean inited = false;
	private static Subject subject;

	static
	{
		init();
	}

	protected static void setSdmxLogger()
	{
		if (SDMX_LOGGER != null)
			getSdmxLogger();

		List<Handler> handlers = new LinkedList<>();
		Logger current = SDMX_LOGGER;
		while (current != null)
		{
			handlers.addAll(Arrays.asList(current.getHandlers()));
			current = current.getUseParentHandlers() ? current.getParent() : null;
		}

		if (handlers.size() == 0)
		{
			// add a default handler if handler is not yet defined
			Handler handler = new SdmxLogHandler();
			handler.setLevel(Level.INFO);
			SDMX_LOGGER.addHandler(handler);
		}
		else if (handlers.size() == 1 && handlers.get(0) instanceof ConsoleHandler && handlers.get(0).getFormatter() instanceof SimpleFormatter)
		{
			// Replace the default consolehandler with a custom handler
			current = SDMX_LOGGER;
			while (current != null)
				if (current.getHandlers().length == 1)
				{
					Handler handler = current.getHandlers()[0];
					Level level = handler.getLevel();
					current.removeHandler(handler);
					handler = new SdmxLogHandler();
					handler.setLevel(level);
					current.addHandler(handler);
					break;
				}
				else
					current = current.getUseParentHandlers() ? current.getParent() : null;
		}
	}

	public static Logger getSdmxLogger()
	{
		return SDMX_LOGGER;
	}

	public static Properties getConfiguration()
	{
		return props;
	}

	public static boolean isReverse()
	{
		return props.getProperty(REVERSE_DUMP_PROP, REVERSE_DUMP_DEFAULT).equalsIgnoreCase("TRUE");
	}

	public static boolean isTable()
	{
		return props.getProperty(TABLE_DUMP_PROP, TABLE_DUMP_DEFAULT).equalsIgnoreCase("TRUE");
	}

	public static String getExternalProviders()
	{
		return props.getProperty(Configuration.EXTERNAL_PROVIDERS_PROP);
	}

	public static int getReadTimeout(String provider)
	{
		String timeout = props.getProperty(provider + "." + Configuration.READ_TIMEOUT_PROP, null);
		if (timeout == null)
		{
			timeout = props.getProperty(Configuration.READ_TIMEOUT_PROP, Configuration.SDMX_DEFAULT_TIMEOUT);
		}
		return Integer.parseInt(timeout);
	}

	public static int getConnectTimeout(String provider)
	{
		String timeout = props.getProperty(provider + "." + Configuration.CONNECT_TIMEOUT_PROP, null);
		if (timeout == null)
		{
			timeout = props.getProperty(Configuration.CONNECT_TIMEOUT_PROP, Configuration.SDMX_DEFAULT_TIMEOUT);
		}
		return Integer.parseInt(timeout);
	}

	public static String getCodesPolicy()
	{
		String policy = props.getProperty(SDMX_CODES_POLICY, SDMX_CODES_POLICY_ID);
		if (!policy.equalsIgnoreCase(SDMX_CODES_POLICY_ID) && !policy.equalsIgnoreCase(SDMX_CODES_POLICY_DESC) && !policy.equalsIgnoreCase(SDMX_CODES_POLICY_BOTH))
		{
			SDMX_LOGGER.warning("The value " + policy + "for the key " + SDMX_CODES_POLICY + " is not valid. Using default.");
			policy = SDMX_CODES_POLICY_ID;
		}
		return policy;
	}

	public static List<LanguageRange> getLanguages()
	{
		init();
		return SDMX_LANG;
	}

	public static String getLateResponseRetries(int defaultRetries)
	{
		return props.getProperty(Configuration.LATE_RESP_RETRIES_PROP, Integer.toString(defaultRetries));
	}

	public static void setLanguages(String languages)
	{
		SDMX_LANG = LanguageRange.parse(languages);
	}

	private static void init()
	{
		synchronized (Configuration.class)
		{
			if (inited)
				return;
			else
				inited = true;
		}

		// normal configuration steps:
		// 1 init LOGGER
		// 2 search configuration in this order: system property, local, global,
		// Configuration class
		// 3 if none is found, apply defaults: no proxy and INFO Logger
		setSdmxLogger();

		String confType = null;

		String confFileName = System.getProperty("SDMX_CONF");
		if (confFileName != null && !confFileName.isEmpty())
		{
			File file = new File(confFileName);
			if (!file.exists())
			{
				confFileName = CONFIGURATION_FILE_NAME;
				System.err.println("Configuration file set by System property: " + confFileName + " not found. Trying default config file.");
			}
		}
		else
			confFileName = CONFIGURATION_FILE_NAME;

		File confFile;
		String globalConfEnvVar = System.getenv(GLOBAL_CONFIGURATION_FILE_PROP);

		// try local configuration.
		if ((confFile = new File(confFileName)).exists())
		{
			try
			{
				// If found apply and exit
				init(confFile);
				confType = System.getProperty("user.dir") + File.separator + confFileName;
				SDMX_LOGGER.info("Local configuration file found: " + confType);
			}
			catch (SecurityException | IOException e)
			{
				e.printStackTrace();
				SDMX_LOGGER.finer(logException(e));
			}
		}
		// try global configuration
		else if (globalConfEnvVar != null && !globalConfEnvVar.isEmpty() && (confFile = new File(globalConfEnvVar)).exists())
			try
			{
				init(confFile);
				confType = globalConfEnvVar;
				SDMX_LOGGER.info("Global configuration file found: " + confType);
			}
			catch (SecurityException | IOException e)
			{
				e.printStackTrace();
				SDMX_LOGGER.finer(logException(e));
			}
		// try configuration class.
		else if (confType == null)
		{
			try
			{
				Class<?> clazz = Class.forName("it.bancaditalia.oss.sdmx.util.SdmxConfiguration");
				Method method = clazz.getMethod("init");
				method.invoke(null);
				confType = clazz.getCanonicalName();
				SDMX_LOGGER.info("Class configuration found: " + confType);
			}
			catch (ClassNotFoundException e)
			{
				SDMX_LOGGER.fine("Class configuration not found, skipping to global conf");
			}
			catch (InvocationTargetException e)
			{
				e.printStackTrace();
				SDMX_LOGGER.info("Error during SdmxConfiguration class initialization, skipping to global conf.");
				SDMX_LOGGER.severe(logException(e.getCause()));
			}
			catch (SecurityException | NoSuchMethodException | IllegalArgumentException | IllegalAccessException e)
			{
				// impossible
				e.printStackTrace();
			}
		}

		System.setProperty("https.protocols", "TLSv1.1,TLSv1.2"); // fix for WB
	}

	private static void init(File file) throws SecurityException, IOException
	{
		try (FileInputStream stream = new FileInputStream(file))
		{
			LogManager.getLogManager().readConfiguration(stream);
		}

		try (FileInputStream stream = new FileInputStream(file))
		{
			props.load(stream);
		}

		// configure SSL
		String tStore = props.getProperty(SSL_TRUSTSTORE_PROP);
		if (tStore != null && !tStore.isEmpty())
			System.setProperty(SSL_TRUSTSTORE_PROP, tStore);

		setupTrustAllCerts();

		// configure default language if not already set explicitly
		SDMX_LANG = LanguageRange.parse(props.getProperty(SDMX_LANG_PROP, SDMX_DEFAULT_LANG));

		configureProxy(props);
	}

	private static void setupTrustAllCerts()
	{
		if ("TRUE".equalsIgnoreCase(props.getProperty(SSL_DISABLE_CERT_CHECK_PROP)))
		{
			SDMX_LOGGER.fine("The SSL Certificate checks are disabled...");
			TrustManager[] alwaysTrust = new TrustManager[] { new X509TrustManager() {
				@Override
				public X509Certificate[] getAcceptedIssuers()
				{
					return null;
				}

				@Override
				public void checkClientTrusted(X509Certificate[] certs, String authType)
				{
				}

				@Override
				public void checkServerTrusted(X509Certificate[] certs, String authType)
				{
				}
			} };

			SSLContext context = null;
			try
			{
				context = SSLContext.getInstance("SSL");
				context.init(null, alwaysTrust, new java.security.SecureRandom());
			}
			catch (NoSuchAlgorithmException e)
			{
				SDMX_LOGGER.fine(logException(e));
			}
			catch (KeyManagementException e)
			{
				SDMX_LOGGER.fine(logException(e));
			}

			if (context != null)
				HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());

			// we also want to avoid verification of the chains
			HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
		}
	}

	public static void setDefaultProxy(String host, String port, String username, String password)
	{
		if (host == null || host.isEmpty() || port == null || port.isEmpty())
			return;
		
		SimpleProxySelector sp = new SimpleProxySelector(host, Integer.parseInt(port));
		ProxySelector.setDefault(sp);
		if (username != null && !username.isEmpty())
			setCredentials(PROXY_AUTH_BASIC, username, password);
	}

	private static void configureProxy(Properties props)
	{
		final String sourceMethod = "configureProxy";
		Logger logger = SDMX_LOGGER;
		logger.entering(sourceClass, sourceMethod);

		// property: http.proxy.default
		String defaultproxy = props.getProperty(PROXY_DEFAULT_PROP);
		String defaultHost = null;
		int defaultPort = 0;
		boolean useProxy = false;
		if (defaultproxy != null && !defaultproxy.isEmpty())
		{
			useProxy = true;
			String[] toks = defaultproxy.split(":");
			if (toks.length != 2 || toks[0] == null || toks[0].isEmpty() || toks[1] == null || toks[1].isEmpty())
			{
				throw new IllegalArgumentException("Proxy settings must be valid. found: '" + defaultproxy + "'");
			}
			defaultHost = toks[0].trim();
			defaultPort = Integer.parseInt(toks[1].trim());
		}
		SdmxProxySelector proxySelector = new SdmxProxySelector(defaultHost, defaultPort);

		for (int i = 0;; i++)
		{
			// property: http.proxy.name_n
			String proxy = props.getProperty(PROXY_NAME_PROP + i);
			if (proxy != null && !proxy.isEmpty())
			{
				useProxy = true;
				String[] toks = null;
				toks = proxy.split(":");
				if (toks == null || toks.length != 2 || toks[0] == null || toks[0].isEmpty() || toks[1] == null || toks[1].isEmpty())
					throw new IllegalArgumentException("Proxy settings must be valid. host: '" + toks[0] + "', port: '" + toks[1] + "'");

				// property: http.proxy.name_n.list
				String urls = props.getProperty(PROXY_NAME_PROP + i + ".urls");
				if (urls != null && !urls.isEmpty())
				{
					String[] urlList = urls.split(",");
					proxySelector.addProxy(toks[0], toks[1], urlList);
					logger.finer("Proxy has been configured: '" + proxy + "' for " + urls);
				}
				else
					throw new IllegalArgumentException("Proxy settings must be valid. host: '" + toks[0] + "', port: '" + toks[1] + "'" + ", urls: " + urls);
			}
			else
				break;
		}

		if (useProxy)
			ProxySelector.setDefault(proxySelector);

		if (props != null && useProxy)
		{
			// get authentication preferences
			String proxyAuth = props.getProperty(HTTP_AUTH_PREF_PROP);
			if (proxyAuth != null)
			{
				proxyAuth = proxyAuth.trim();
				System.setProperty(HTTP_AUTH_PREF_PROP, proxyAuth);
				logger.finer(proxyAuth + " authentication enabled.");

				if (proxyAuth.equalsIgnoreCase(PROXY_AUTH_KERBEROS))
				{
					// set properties for JAAS
					String conf = props.getProperty(JAVA_SECURITY_KERBEROS_PROP);
					String login = props.getProperty(JAVA_SECURITY_AUTH_LOGIN_CONFIG_PROP);
					String krbccname = System.getenv().get("KRB5CCNAME");

					if (krbccname != null && login != null && conf != null)
					{
						krbccname = krbccname.trim();
						login = login.trim();
						conf = conf.trim();
						System.setProperty("user.krb5cc", krbccname);
						// System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
						System.setProperty(JAVA_SECURITY_KERBEROS_PROP, conf);
						System.setProperty(JAVA_SECURITY_AUTH_LOGIN_CONFIG_PROP, login);
						logger.finer(JAVA_SECURITY_KERBEROS_PROP + " = " + conf);
						logger.finer(JAVA_SECURITY_AUTH_LOGIN_CONFIG_PROP + " = " + login);
						logger.finer("Environment variable KRB5CCNAME = " + krbccname);
					}
					else
					{
						logger.warning("Kerberos ticket cache not configured because one of the parameters is not set.");
						logger.warning(JAVA_SECURITY_KERBEROS_PROP + " = " + conf);
						logger.warning(JAVA_SECURITY_AUTH_LOGIN_CONFIG_PROP + " = " + login);
						logger.warning("Environment variable KRB5CCNAME = " + krbccname);
					}
				}
				else if (proxyAuth.equalsIgnoreCase(PROXY_AUTH_BASIC))
				{
					String username = props.getProperty(HTTP_AUTH_USER_PROP);
					String password = props.getProperty(PROXY_AUTH_PW_PROP);
					setCredentials(proxyAuth, username, password);
					// remove the password for security reasons
					props.remove(PROXY_AUTH_PW_PROP);
				}
				else
					logger.finer("Authentication type not supported: " + proxyAuth);
			}
			else
				logger.finer("No authentication enabled.");
		}

		logger.exiting(sourceClass, sourceMethod);
	}

	private static void setCredentials(String scheme, String username, String password)
	{
		System.setProperty(HTTP_AUTH_PREF_PROP, scheme);
		if (username == null || password == null)
		{
			final JFrame frame = new JFrame("Proxy Authentication");
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			LoginDialog loginDlg = new LoginDialog(frame, "Proxy Authentication");
			loginDlg.setVisible(true);
			username = loginDlg.getUsername();
			password = loginDlg.getPassword();
			frame.dispose();
		}

		final String user = username.trim();
		final String pw = password.trim();
		Authenticator.setDefault(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication()
			{
				final String sourceMethod = "getPasswordAuthentication";
				Logger logger = SDMX_LOGGER;
				logger.entering(sourceClass, sourceMethod);

				PasswordAuthentication p = new PasswordAuthentication(user, pw.toCharArray());
				logger.finer("Requesting Host  : " + getRequestingHost());
				logger.finer("Requesting Port  : " + getRequestingPort());
				logger.finer("Requesting Protocol: " + getRequestingProtocol());
				logger.finer("Requesting Scheme : " + getRequestingScheme());

				logger.exiting(sourceClass, sourceMethod);
				return p;
			}
		});
	}

	public static boolean isWindows()
	{
		String os = System.getProperty("os.name").toLowerCase();
		// windows
		return os.indexOf("win") >= 0;
	}

	public static String getUISApiKey()
	{
		return props.getProperty(Configuration.UIS_API_KEY_PROP, null);
	}

	private static String logException(Throwable t)
	{
		StringWriter wr = new StringWriter();
		t.printStackTrace(new PrintWriter(wr));
		wr.flush();
		return wr.toString();
	}

	public static String getDumpPrefix()
	{
		String path = props.getProperty(DUMP_XML_PREFIX);
		if (path == null || path.isEmpty())
		{
			SDMX_LOGGER.warning("The directory set for storing xml files is not correctly set.");
			props.remove(DUMP_XML_PREFIX);
		}
		return path;
	}

	public static void setDumpPrefix(String path)
	{
		if (path == null || path.isEmpty())
			SDMX_LOGGER.warning("The directory for storing xml files cannot be null");
		else
		{
			File f = new File(path);
			if (f.exists() && f.isDirectory())
				props.put(DUMP_XML_PREFIX, path);
			else
				SDMX_LOGGER.warning("The directory for storing xml files must already exist");
		}
	}

	public static boolean isDumpXml()
	{
		return (props.getProperty(DUMP_XML_PREFIX) != null) && (!props.getProperty(DUMP_XML_PREFIX).isEmpty());
	}

	public static void setSubject(Subject subject)
	{
		Configuration.subject = subject;
	}

	public static Subject getSubject()
	{
		return subject;
	}
}
