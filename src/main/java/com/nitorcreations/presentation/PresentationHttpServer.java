package com.nitorcreations.presentation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;


@SuppressWarnings("restriction")
public class PresentationHttpServer {
	private Map<String, byte[]> contents = new HashMap<>();
	private Map<String, String> md5sums = new ConcurrentHashMap<>();
	MessageDigest md5;
	
	public PresentationHttpServer(int port) throws IOException, NoSuchAlgorithmException {
		InetSocketAddress addr = new InetSocketAddress(port);
		HttpServer server = HttpServer.create(addr, 0);

		server.createContext("/", new RequestHandler());
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		System.out.println("Server is listening on port " + port );
		md5 = MessageDigest.getInstance("MD5");
	}

	class RequestHandler implements HttpHandler {
		public void handle(HttpExchange exchange) throws IOException {
			String requestMethod = exchange.getRequestMethod();
			if (requestMethod.equalsIgnoreCase("GET")) {
				Headers responseHeaders = exchange.getResponseHeaders();
				URI uri = exchange.getRequestURI();
				String resourceName = "html" + uri.getPath();
				if (uri.getPath().equals("/")) {
					resourceName = "html/index.html";
				}
				List<String> inmatch = exchange.getRequestHeaders().get("If-None-Match");
				List<String> range = exchange.getRequestHeaders().get("Range");
				if (inmatch != null && inmatch.size() > 0 && inmatch.get(0).equals(md5sums.get(resourceName)) && 
						(range == null || range.size() == 0)) {
					responseHeaders.set("Accept-Ranges", "bytes");
					responseHeaders.set("ETag", inmatch.get(0));
					exchange.sendResponseHeaders(304, -1);
					return;
				}
				byte[] content = getContent(resourceName);
				if (content != null) {
					if (uri.getPath().endsWith(".html")) {
						responseHeaders.set("Content-Type", "text/html");
					}
					if (uri.getPath().toLowerCase().endsWith(".png")) {
						responseHeaders.set("Content-Type", "image/png");
					}
					if (uri.getPath().toLowerCase().endsWith(".jpg") ||
							uri.getPath().toLowerCase().endsWith(".jpeg")) {
						responseHeaders.set("Content-Type", "image/jpeg");
					}
					if (uri.getPath().toLowerCase().endsWith(".mp4")) {
						responseHeaders.set("Content-Type", "video/mp4");
					}
					if (uri.getPath().toLowerCase().endsWith(".ogv")) {
						responseHeaders.set("Content-Type", "video/ogg");
					}
					responseHeaders.set("Accept-Ranges", "bytes");
					String md5sum = md5sums.get(resourceName);
					responseHeaders.set("ETag", md5sum);
					if (range == null || range.size() == 0) {
						exchange.sendResponseHeaders(200, content.length);
						OutputStream responseBody = exchange.getResponseBody();
						responseBody.write(content);
						responseBody.close();
						return;
					} else {
						System.out.println("RANGE request for "  + exchange.getRequestURI().getPath());
						System.out.println("RANGE: " + range.get(0));
						List<Range> ranges = new ArrayList<>();
						for (String nextRange : range) {
							Range currentRange = new Range();
							currentRange.length = content.length;
							if (nextRange.startsWith("bytes=")) {
								nextRange = nextRange.substring(6);
							}
							int dashPos = nextRange.indexOf('-');
							if (dashPos == 0) {
								try {
									int offset = Integer.parseInt(nextRange);
									currentRange.start = content.length - offset;
									currentRange.end = content.length  - 1;
								} catch (NumberFormatException e) {
									responseHeaders.set("Content-Range", "bytes */" + content.length);
									exchange.sendResponseHeaders(416,  -1);
									return;
								}
							} else {
								try {
									currentRange.start = Integer.parseInt(nextRange.substring(0, dashPos));
									if (dashPos < nextRange.length() - 1) {
										currentRange.end = Integer.parseInt(nextRange.substring
												(dashPos + 1, nextRange.length()));
									} else {
										currentRange.end = content.length-1;
									}
								} catch (NumberFormatException e) {
									responseHeaders.set("Content-Range", "bytes */" + content.length);
									exchange.sendResponseHeaders(416,  -1);
									return;
								}
							}
							if (!currentRange.validate()) {
								responseHeaders.set("Content-Range", "bytes */" + content.length);
								exchange.sendResponseHeaders(416,  -1);
								return;
							}
							ranges.add(currentRange);
						}
						Range ret = ranges.get(0);
						responseHeaders.set("Content-Range", "bytes " + ret.start
								+ "-" + ret.end + "/"
								+ ret.length);
						exchange.sendResponseHeaders(206, ret.end - ret.start + 1);
						OutputStream responseBody = exchange.getResponseBody();
						responseBody.write(content, ret.start, ret.end - ret.start + 1);
						responseBody.close();
					}
				} else {
					responseHeaders.set("Content-Type", "text/plain");
					exchange.sendResponseHeaders(404, 0);
					OutputStream responseBody = exchange.getResponseBody();
					responseBody.write("Not found".getBytes());
					responseBody.close();

				}
			} else if (requestMethod.equalsIgnoreCase("HEAD")) {
				System.out.println("HEAD request for "  + exchange.getRequestURI().getPath());
			}
		}
	}
	
	private synchronized byte[] getContent(String resourceName) throws IOException {
		byte[] cached = contents.get(resourceName);
		if (cached == null) {
			try (InputStream in = Utils.getResource(resourceName)) {
				if (in == null) {
					return null;
				}
				byte[] buffer = new byte[1024];
				int len = in.read(buffer);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				while (len != -1) {
					out.write(buffer, 0, len);
					len = in.read(buffer);
				}
				cached = out.toByteArray();
				md5.reset();
				byte[] digest = md5.digest(cached);
				StringBuffer sb = new StringBuffer();
				for (int i = 0; i < digest.length; ++i) {
					sb.append(Integer.toHexString((digest[i] & 0xFF) | 0x100).substring(1,3));
				}
				md5sums.put(resourceName, sb.toString());
				contents.put(resourceName, cached);
			}
		}
		return cached;
	}

	
    protected static class Range {

        public int start;
        public int end;
        public int length;

        /**
         * Validate range.
         */
        public boolean validate() {
            if (end >= length)
                end = length - 1;
            return (start >= 0) && (end >= 0) && (start <= end) && (length > 0);
        }
    }
}