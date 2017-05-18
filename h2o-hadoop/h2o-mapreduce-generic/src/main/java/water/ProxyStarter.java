package water;

import water.init.NetworkInit;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

public class ProxyStarter {

  public static String start(String[] args, String proxyTo) {
    if (! proxyTo.endsWith("/"))
      proxyTo = proxyTo + "/";
    // Note: this is UGLY!!!
    // We populate H2O.ARGS because they are accessed in NetworkInit.findInetAddressForSelf()
    // FIXME: Refactor NetworkInit.findInetAddressForSelf() to work without H2O.ARGS
    H2O.parseArguments(args);
    H2O.BaseArgs baseArgs = H2O.ARGS;

    JettyProxy proxy = initializeProxy(baseArgs, proxyTo);

    H2O.API_PORT = proxy.getPort();
    H2O.SELF_ADDRESS = NetworkInit.findInetAddressForSelf();
    return H2O.getURL(proxy.getScheme());
  }

  private static JettyProxy initializeProxy(H2O.BaseArgs args, String proxyTo) {
    int proxyPort = args.port == 0 ? args.baseport : args.port;

    JettyProxy proxy = new JettyProxy(args, proxyTo);

    // PROXY socket is only used to find opened port on given ip
    ServerSocket proxySocket = null;

    while (true) {
      try {
        proxySocket = args.web_ip == null ? // Listen to any interface
                new ServerSocket(proxyPort) : new ServerSocket(proxyPort, -1, getInetAddress(args.web_ip));
        proxySocket.setReuseAddress(true);

        // race condition: another process can use the port while proxy is starting
        proxySocket.close();
        proxy.start(args.web_ip, proxyPort);

        break;
      } catch (Exception e) {
        System.err.println("TRACE: Cannot allocate API port " + proxyPort + " because of following exception: " + e.getMessage());
        if (proxySocket != null) try { proxySocket.close(); } catch (IOException ee) { System.err.println("TRACE: " + ee.getMessage()); }
        proxySocket = null;
        if (args.port != 0)
          throw new RuntimeException("Port " + proxyPort + " is not available, change -port PORT and try again.");
      }
      // Try next available port to bound
      proxyPort += 2;
      if (proxyPort > (1 << 16))
        throw new RuntimeException("Cannot find free port from baseport = " + args.baseport);
    }

    return proxy;
  }

  private static InetAddress getInetAddress(String ip) {
    if (ip == null)
      return null;

    try {
      return InetAddress.getByName(ip);
    } catch (UnknownHostException e) {
      throw new RuntimeException("Unable to resolve the host", e);
    }
  }

  // just for local testing
  public static void main(String[] args) {
    String url = start(args, "https://localhost:54321/");
    System.out.println("Proxy started on " + url);
  }

}