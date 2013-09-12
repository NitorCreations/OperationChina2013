package com.nitorcreations.presentation;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Utils {

	public static String[] getResourceListing(String path) {
		URL dirURL = Utils.class.getClassLoader().getResource(path);
		if (dirURL == null) {
			String me = Utils.class.getName().replace(".", "/")+".class";
			dirURL = Utils.class.getClassLoader().getResource(me);
		}
		if (dirURL != null) {
			if  (dirURL.getProtocol().equals("file")) {
				try {
					File[] files = new File(dirURL.toURI()).listFiles();
					String[] ret = new String[files.length];
					for (int i = 0; i < files.length; i++) {
						if (files[i].isDirectory()) {
							ret[i] = files[i].getName() + "/";
						} else {
							ret[i] = files[i].getName();
						}
					}
					return ret;
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
			} else if (dirURL.getProtocol().equals("jar")) {
				String jarPath = dirURL.getPath().substring(5, dirURL.getPath().indexOf("!"));
				Set<String> entries = Utils.getJarEntries(jarPath); 
				Set<String> result = new HashSet<String>(); 
				for (String name : entries) {
					if (name.startsWith(path)) {
						String entry = name.substring(path.length());
						int checkSubdir = entry.indexOf("/");
						if (checkSubdir >= 0) {
							entry = entry.substring(0, checkSubdir + 1);
						}
						result.add(entry);
					}
				}
				return result.toArray(new String[result.size()]);
			} 
		}
		throw new UnsupportedOperationException("Cannot list files for URL "+dirURL);
	}

	public static synchronized Set<String> getJarEntries(String jarPath) {
		Set<String> result = new HashSet<String>();
		JarFile jar = null;
		try {
			File jarFile = new File(URLDecoder.decode(jarPath, "UTF-8"));
			if (PresentationController.jarEntryCache.containsKey(jarFile.getAbsolutePath())) {
				return PresentationController.jarEntryCache.get(jarFile.getAbsolutePath());
			} else {
				jar = new JarFile(jarFile);
				Enumeration<JarEntry> entries = jar.entries(); 
				while(entries.hasMoreElements()) {
					result.add(entries.nextElement().getName());
				}
				PresentationController.jarEntryCache.put(jarFile.getAbsolutePath(), result);
			}
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (jar != null) {
				try {
					jar.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return result;
	}

	public static void runVideo(final File video) {
		try {
			Process p = Runtime.getRuntime().exec("videoplayer " + video.getAbsolutePath());

			final BufferedReader stdInput = new BufferedReader(new 
					InputStreamReader(p.getInputStream()));

			final BufferedReader stdError = new BufferedReader(new 
					InputStreamReader(p.getErrorStream()));

			new Thread(new Runnable() {
				String s = null;
				public void run() {
					try {
						while ((s = stdInput.readLine()) != null) {
							System.out.println(s);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				};
			}).start();

			new Thread(new Runnable() {
				String s = null;
				public void run() {
					try {
						while ((s = stdError.readLine()) != null) {
							System.out.println(s);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}).start();
			p.waitFor();
		}
		catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	public static InputStream getResource(String name) {
		System.out.println("Getting resource: " + name);
		return Utils.class.getClassLoader().getResourceAsStream(name);
	}

}
