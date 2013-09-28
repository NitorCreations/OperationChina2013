package com.nitorcreations.presentation;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javafx.scene.image.ImageView;


import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;


@SuppressWarnings("restriction")
public class PresentationHttpServer {
	private Map<String, byte[]> contents = new HashMap<>();
	private Map<String, String> md5sums = new ConcurrentHashMap<>();
	MessageDigest md5;

	public PresentationHttpServer(int port, PresentationController controller) throws IOException, NoSuchAlgorithmException {
		InetSocketAddress addr = new InetSocketAddress(port);
		HttpServer server = HttpServer.create(addr, 0);

		HttpContext cc = server.createContext("/run/", new RequestHandler("run", controller));
		if (System.getProperty("httprunpasswords") != null) {
			Properties passwd = new Properties();
			passwd.load(new FileInputStream(System.getProperty("httprunpasswords")));
			cc.setAuthenticator(new DigestAuthenticator(passwd, "run-presentation"));
		}
		cc = server.createContext("/follow/", new RequestHandler("follow", controller));
		if (System.getProperty("httpfollowpasswords") != null) {
			Properties passwd = new Properties();
			passwd.load(new FileInputStream(System.getProperty("httpfollowpasswords")));
			cc.setAuthenticator(new DigestAuthenticator(passwd, "follow-presentation"));
		}
		cc = server.createContext("/", new RequestHandler(""));
		if (System.getProperty("httpdefaultpasswords") != null) {
			Properties passwd = new Properties();
			passwd.load(new FileInputStream(System.getProperty("httpdefaultpasswords")));
			cc.setAuthenticator(new DigestAuthenticator(passwd, "default-presentation"));
		}
		cc = server.createContext("/download/", new DownloadHandler());
		if (System.getProperty("httpdownloadpasswords") != null) {
			Properties passwd = new Properties();
			passwd.load(new FileInputStream(System.getProperty("httpdownloadpasswords")));
			cc.setAuthenticator(new DigestAuthenticator(passwd, "download-presentation"));
		}
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		System.out.println("Server is listening on port " + port );
		md5 = MessageDigest.getInstance("MD5");
	}

	class RequestHandler implements HttpHandler {
		private final String context;
		private PresentationController controller = null;
		
		public RequestHandler(String context) {
			this.context = context;
		}
		
		public RequestHandler(String context, PresentationController controller) {
			this.context = context;
			this.controller = controller;
		}
		
		public void handle(HttpExchange exchange) throws IOException {
			String requestMethod = exchange.getRequestMethod();
			if (requestMethod.equalsIgnoreCase("GET")) {
				Headers responseHeaders = exchange.getResponseHeaders();
				responseHeaders.set("Accept-Ranges", "bytes");

				URI uri = exchange.getRequestURI();
				String path = uri.getPath().substring(context.length() + 1);
				if (path.startsWith("/")) {
					path = path.substring(1);
				}
				
				if (path.startsWith("show") && context.equals("run")) {
					responseHeaders.set("Content-Type", "text/plain");
					try {
						int slideIndex = Integer.parseInt(path.split("/")[1]);
						if (path.startsWith("showquick")) {
							controller.showSlide(slideIndex, true);
						} else {
							controller.showSlide(slideIndex);
						}
						exchange.sendResponseHeaders(200, 0);
						OutputStream responseBody = exchange.getResponseBody();
						responseBody.write(Integer.toString(controller.curentSlide()).getBytes());
						responseBody.close();
					} catch (NullPointerException | ArrayIndexOutOfBoundsException | NumberFormatException e) {
						System.out.println("Illegal slide show command " + path);
						exchange.sendResponseHeaders(400, 0);
						OutputStream responseBody = exchange.getResponseBody();
						responseBody.write(("Bad request: " + path).getBytes());
						responseBody.close();
					}
					return;
				}

				if (path.startsWith("currentslide") && (context.equals("run") || context.equals("follow"))) {
					responseHeaders.set("Content-Type", "text/plain");
					exchange.sendResponseHeaders(200, 0);
					OutputStream responseBody = exchange.getResponseBody();
					responseBody.write(Integer.toString(controller.curentSlide()).getBytes());
					responseBody.close();
					return;
				}

				if (path.startsWith("slidecount") && (context.equals("run") || context.equals("follow"))) {
					responseHeaders.set("Content-Type", "text/plain");
					exchange.sendResponseHeaders(200, 0);
					OutputStream responseBody = exchange.getResponseBody();
					responseBody.write(Integer.toString(controller.curentSlide()).getBytes());
					responseBody.close();
					return;
				}
				
				
				String resourceName = "html/" + path; 
				if ("html/".equals(resourceName) || resourceName.startsWith("html/index")) {
					if (context.length() == 0) {
						resourceName = "html/index-default.html";
					} else {
						resourceName = "html/index-" + context + ".html";
					}
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
				responseHeaders.set("ETag", md5sums.get(resourceName));
				if (content != null) {
					if (uri.getPath().endsWith(".html")) {
						responseHeaders.set("Content-Type", "text/html");
					} else if (uri.getPath().toLowerCase().endsWith(".png")) {
						responseHeaders.set("Content-Type", "image/png");
					} else if (uri.getPath().toLowerCase().endsWith(".jpg") ||
							uri.getPath().toLowerCase().endsWith(".jpeg")) {
						responseHeaders.set("Content-Type", "image/jpeg");
					} else if (uri.getPath().toLowerCase().endsWith(".mp4")) {
						responseHeaders.set("Content-Type", "video/mp4");
					} else if (uri.getPath().toLowerCase().endsWith(".ogv")) {
						responseHeaders.set("Content-Type", "video/ogg");
					}
					if (range == null || range.size() == 0) {
						exchange.sendResponseHeaders(200, content.length);
						OutputStream responseBody = exchange.getResponseBody();
						responseBody.write(content);
						responseBody.close();
						return;
					} else {
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
						exchange.sendResponseHeaders(206, ret.rangeLen);
						OutputStream responseBody = exchange.getResponseBody();
						responseBody.write(content, ret.start, ret.rangeLen);
						responseBody.close();
					}
				} else {
					responseHeaders.set("Content-Type", "text/plain");
					exchange.sendResponseHeaders(404, 0);
					OutputStream responseBody = exchange.getResponseBody();
					responseBody.write("Not found".getBytes());
					responseBody.close();

				}
			}
		}
	}
	
	class DownloadHandler implements HttpHandler {

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			Headers responseHeaders = exchange.getResponseHeaders();
			String path = exchange.getRequestURI().getPath().substring("/download".length());
			if (path.startsWith("/")) {
				path = path.substring(1);
			}
			if ((path.startsWith("slides") || path.startsWith("html")) && path.endsWith(".zip")) {
				String slideSet = path.substring(0, path.length() - 4);
				String slideDir = slideSet + "/";
				String[] slides = Utils.getResourceListing(slideDir);
				if (slides == null || slides.length == 0) {
					responseHeaders.set("Content-Type", "text/plain");
					exchange.sendResponseHeaders(404, 0);
					OutputStream responseBody = exchange.getResponseBody();
					responseBody.write("Not found".getBytes());
					responseBody.close();
					return;
				}
				responseHeaders.set("Content-Type", "application/zip");
				responseHeaders.set("Content-disposition", "attachment; filename=" + path);
				exchange.sendResponseHeaders(200, 0);
				ZipOutputStream out = new ZipOutputStream(exchange.getResponseBody());
				List<String> slideNames = Arrays.asList(slides);
				Collections.sort(slideNames);
				for (String next : slideNames) {
					if (next.isEmpty() || next.equals("/")) continue;
					if (next.endsWith(".video")) {
						try (BufferedReader in = 
								new BufferedReader(new InputStreamReader(Utils.getResource(slideDir + next)))) {
							String video=in.readLine();
							writeNextEntry(out, Utils.getResource("html/" + video), next + "." + video);
						} catch (IOException e) {
							e.printStackTrace();
						}
					} else {
						writeNextEntry(out, Utils.getResource(slideDir + next), next);
					}	
				}
				out.close();
			} else if (path.equals("presentation-small-images.zip")) {
				responseHeaders.set("Content-Type", "application/zip");
				responseHeaders.set("Content-disposition", "attachment; filename=presentation-images.zip");
			} else if (path.equals("presentation-small-images.zip")) {
				responseHeaders.set("Content-Type", "application/zip");
				responseHeaders.set("Content-disposition", "attachment; filename=presentation-images.zip");
			} else {
				responseHeaders.set("Content-Type", "text/plain");
				exchange.sendResponseHeaders(404, 0);
				OutputStream responseBody = exchange.getResponseBody();
				responseBody.write("Not found".getBytes());
				responseBody.close();
			}
		}
		
		final byte[] buffer = new byte[1024];
    	
		private void writeNextEntry(ZipOutputStream out, InputStream in, String name) {
	        try {
	        	out.putNextEntry(new ZipEntry(name));
	        	int count;

	        	while ((count = in.read(buffer)) > 0) {
	        		out.write(buffer, 0, count);
	        	}
	        } catch (IOException e) {
	        	e.printStackTrace();
	        } finally {
	        	if (in != null) {
	        		try {
						in.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
	        	}
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
				md5sums.put(resourceName, Utils.toHexString(digest));
				if (System.getProperty("nocache") == null) {
					contents.put(resourceName, cached);
				}
			}
		}
		return cached;
	}


	protected static class Range {

		public int start;
		public int end;
		public int rangeLen;
		public int length;

		public boolean validate() {
			if (end >= length)
				end = length - 1;
			rangeLen=end-start+1;
			return (start >= 0) && (end >= 0) && (start <= end) && (length > 0);
		}
	}
}