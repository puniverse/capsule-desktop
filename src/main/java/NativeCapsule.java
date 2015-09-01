/*
 * Copyright (c) 2014-2015, Parallel Universe Software Co. and Contributors. All rights reserved.
 *
 * This program and the accompanying materials are licensed under the terms
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import capsule.GUIListener;
import ch.qos.logback.classic.Level;
import co.paralleluniverse.capsule.*;
import net.sf.launch4j.Builder;
import net.sf.launch4j.Log;
import net.sf.launch4j.config.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Arrays.*;

/**
 * Wrapping capsule that will build and launch a native desktop GUI or non-GUI app.
 *
 * @author pron
 * @author circlespainter
 */
public class NativeCapsule {

	protected static final String ATTR_GUI = "GUI";
	protected static final String ATTR_ICON = "Icon";
	protected static final String ATTR_SINGLE_INSTANCE = "Single-Instance";

	protected static final String ATTR_IMPLEMENTATION_VENDOR = "Implementation-Vendor";

	protected static final String ATTR_NATIVE_DESCRIPTION = "Native-Description";
	protected static final String ATTR_COPYRIGHT = "Copyright";
	protected static final String ATTR_INTERNAL_NAME = "Internal-Name";

	private static final String GUI_CAPSULE_NAME = "GUICapsule";
	private static final String MAVEN_CAPSULE_NAME = "MavenCapsule";
	private static final String GUI_MAVEN_CAPSULE_NAME = "GUIMavenCapsule";

	private static Logger log = LoggerFactory.getLogger(NativeCapsule.class);

	private static List<Path> tmpFiles = new ArrayList<>();
	private static Path inCapsulePath;
	private static String outBasePath;
	private static co.paralleluniverse.capsule.Capsule inCapsule;
	private static boolean buildMac, buildUnix, buildWindows;

	public static void main(String[] args) throws IOException {
		final OptionParser parser = new OptionParser();
		final OptionSpec<String> c = parser.acceptsAll(asList("c", "capsule")).withRequiredArg().ofType(String.class).describedAs("A single capsule pathname to build native binaries for");
		final OptionSpec<String> o = parser.acceptsAll(asList("o", "output")).withRequiredArg().ofType(String.class).describedAs("The base output pathname of built binaries (default = the capsule pathname)");
		final OptionSpec<String> l = parser.acceptsAll(asList("l", "loglevel")).withRequiredArg().ofType(String.class).describedAs("Log level (default = INFO)");
		parser.acceptsAll(asList("m", "macosx"), "Build Mac OS X binary");
		parser.acceptsAll(asList("u", "unix"), "Build Unix binary");
		parser.acceptsAll(asList("w", "windows"), "Build Windows binary");
		parser.acceptsAll(asList("h", "?", "help"), "Show help").forHelp();
		final OptionSet options = parser.parse(args);

		if (!options.has(c) || options.valuesOf(c).size() != 1 || options.valuesOf(o).size() > 1 || options.valuesOf(l).size() > 1) {
			log.error("Command-line validation failed");
			parser.printHelpOn(System.err);
			System.exit(-1);
		}

		if (options.has(l))
			((ch.qos.logback.classic.Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.toLevel(options.valueOf(l), Level.INFO));

		inCapsulePath = Paths.get(options.valueOf(c));
		log.debug("Input capsule: {}", inCapsulePath.toAbsolutePath().normalize().toString());
		inCapsule = new CapsuleLauncher(inCapsulePath).newCapsule();
		log.debug("Output binary prefix: {}", outBasePath);
		buildMac = options.has("m") || options.has("macosx");
		buildUnix = options.has("u") || options.has("unix");
		buildWindows = options.has("w") || options.has("windows");

		buildNative();

		log.debug("Removing temp files");
		for (final Path p : tmpFiles)
			Capsule.delete(p);
	}

	private static String getOutputBase() {
		return getOutputBase(null);
	}

	private static String getOutputBase(String outBase) {
		if (outBase == null) {
			outBase = inCapsulePath.toAbsolutePath().normalize().toString();
			if (outBase.toLowerCase().endsWith(".jar"))
				outBase = outBase.substring(0, outBase.indexOf(".jar"));
		}
		return outBase;
	}

	private static void buildNative() {
		final String outBase = getOutputBase(outBasePath);
		try {
			final List<String> platforms = new ArrayList<>();
			if (buildMac)
				platforms.add(Platform.OS_MACOS);
			if (buildUnix)
				platforms.add(Platform.OS_UNIX);
			if (buildWindows)
				platforms.add(Platform.OS_WINDOWS);

			if (platforms.isEmpty())
				platforms.add("CURRENT"); // Default

			log.debug("Building native binaries for the following platforms: {}", platforms.toString());

			for (final String p : platforms)
				buildApp(p, Paths.get(outBase));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void buildApp(String platform, Path out) throws IOException {
		if (Platform.OS_MACOS.equals(platform))
			buildMacApp(out);
		else if (Platform.OS_UNIX.equals(platform))
			buildUnixApp(out);
		else if (Platform.OS_WINDOWS.equals(platform))
			buildWindowsApp(out);
		else if ("CURRENT".equals(platform))
			buildApp(Platform.myPlatform().getOS(), out);
		else
			throw new RuntimeException("Platform \"" + platform + "\" is unsupported");
	}

	private static String getSimpleCapsuleName() {
		final String filename = inCapsulePath.getFileName().toString();
		return filename.endsWith(".jar") ? filename.substring(0, filename.length() - 4) : filename;
	}

	private static Jar createJar(Path out) throws IOException {
		final Jar jar = new Jar(inCapsulePath);
		if (out != null) {
			log.debug("Creating JAR for native app: {}", out.toAbsolutePath().normalize().toString());
			jar.setOutput(out);
		}
		return jar;
	}

	private static boolean isGUIApp() {
		if (inCapsule.hasAttribute(Attribute.<Boolean>named(ATTR_GUI)))
			return inCapsule.getAttribute(Attribute.<Boolean>named(ATTR_GUI));
		return false;
	}

	private static Path buildWindowsApp(Path out) throws IOException {
		log.debug("Building native Windows app: {}", out.toAbsolutePath().normalize().toString());

		setLaunch4JBinDir();
		setLaunch4JLibDir();
		setLaunch4JHeadDir();
		setLaunch4JTmpDir();

		Path tmpJar = null;
		Path icon = null;
		try {
			if (isGUIApp()) {
				tmpJar = Files.createTempFile("native-capsule-", ".jar");
				log.debug("Creating Windows temp jar {}", tmpJar.toFile().toString());
				Jar j = createJar(tmpJar);
				makeGUICapsule(j);
				j.close();
			}
			final Path jar = tmpJar != null ? tmpJar : inCapsulePath;

			ConfigPersister.getInstance().createBlank();
			final Config c = ConfigPersister.getInstance().getConfig();
			final String head = isGUIApp() ? Config.GUI_HEADER : Config.CONSOLE_HEADER;
			log.debug("Windows: using head type {}", head);
			c.setHeaderType(head);
			c.setOutfile(withSuffix(out, ".exe").toFile());
			log.debug("Windows: using jar {}", jar.toAbsolutePath().normalize().toString());
			log.debug("Windows: writing to {}", c.getOutfile().toString());
			c.setJar(jar.toFile());

			if (inCapsule.hasAttribute(Attribute.named(Capsule.ATTR_MIN_JAVA_VERSION.getKey()))) {
				final String minJavaVersion = inCapsule.getAttribute(Attribute.<String>named(Capsule.ATTR_MIN_JAVA_VERSION.getKey()));
				log.debug("Windows: requiring minumum Java version {}", minJavaVersion);
				c.getJre().setMinVersion(minJavaVersion);
			}
			if (inCapsule.hasAttribute(Attribute.named(Capsule.ATTR_JAVA_VERSION.getKey()))) {
				final String maxJavaVersion = inCapsule.getAttribute(Attribute.<String>named(Capsule.ATTR_JAVA_VERSION.getKey()));
				log.debug("Windows: requiring maximum Java version {}", maxJavaVersion);
				c.getJre().setMaxVersion(maxJavaVersion);
			}
			if (inCapsule.hasAttribute(Attribute.named(Capsule.ATTR_JDK_REQUIRED.getKey()))) {
				final String jdkPreference = inCapsule.<Boolean>getAttribute(Attribute.<Boolean>named(Capsule.ATTR_JDK_REQUIRED.getKey())) ?
					Jre.JDK_PREFERENCE_JDK_ONLY : null;
				log.debug("Windows: JDK preferred? {}", Jre.JDK_PREFERENCE_JDK_ONLY.equals(jdkPreference) ? "true" : "false");
				log.debug("Windows: JDK preferred = {}", Jre.JDK_PREFERENCE_JDK_ONLY.equals(jdkPreference) ? "true" : "false");
				c.getJre().setJdkPreference(jdkPreference);
			}

			if (inCapsule.hasAttribute(Attribute.<Boolean>named(ATTR_SINGLE_INSTANCE)) && inCapsule.getAttribute(Attribute.<Boolean>named(ATTR_SINGLE_INSTANCE))) {
				log.debug("Windows: restricting to single instance as requested");
				final SingleInstance si = new SingleInstance();
				si.setWindowTitle(inCapsule.getAttribute(Attribute.<String>named(Capsule.ATTR_APP_NAME.getKey())));
				si.setMutexName(inCapsule.getAppId());
				c.setSingleInstance(si);
			}

			if (inCapsule.getAttribute(Attribute.<String>named(ATTR_IMPLEMENTATION_VENDOR)) != null
				|| inCapsule.getAttribute(Attribute.<String>named(ATTR_NATIVE_DESCRIPTION)) != null
				|| inCapsule.getAttribute(Attribute.<String>named(ATTR_COPYRIGHT)) != null
				|| inCapsule.getAttribute(Attribute.<String>named(ATTR_INTERNAL_NAME)) != null) {
				log.debug("Windows: detected metadata attributes, setting them");

				final VersionInfo versionInfo = new VersionInfo();
				versionInfo.setCompanyName(inCapsule.getAttribute(Attribute.<String>named(ATTR_IMPLEMENTATION_VENDOR)));
				versionInfo.setProductName(inCapsule.getAttribute(Attribute.<String>named(Capsule.ATTR_APP_NAME.getKey())));
				versionInfo.setFileVersion(versionToWindowsVersion(inCapsule.getAttribute(Attribute.<String>named(Capsule.ATTR_APP_VERSION.getKey()))));
				versionInfo.setFileDescription(inCapsule.getAttribute(Attribute.<String>named(ATTR_NATIVE_DESCRIPTION)));
				versionInfo.setCopyright(inCapsule.getAttribute(Attribute.<String>named(ATTR_COPYRIGHT)));
				versionInfo.setInternalName(inCapsule.getAttribute(Attribute.<String>named(ATTR_INTERNAL_NAME)));
				versionInfo.setOriginalFilename(withSuffix(out, ".exe").toFile().getName());
				versionInfo.setProductVersion(versionToWindowsVersion(inCapsule.getAttribute(Attribute.<String>named(Capsule.ATTR_APP_VERSION.getKey()))));
				versionInfo.setTxtFileVersion(inCapsule.getAttribute(Attribute.<String>named(Capsule.ATTR_APP_VERSION.getKey())));
				versionInfo.setTxtProductVersion(inCapsule.getAttribute(Attribute.<String>named(Capsule.ATTR_APP_VERSION.getKey())));
				c.setVersionInfo(versionInfo);
			}

			if (inCapsule.hasAttribute(Attribute.<String>named(ATTR_ICON))) {
				final URLClassLoader urlClassLoader = new URLClassLoader( new URL[] { jar.toUri().toURL() } );
				InputStream input = null;
				String resName = null;
				try {
					resName = inCapsule.getAttribute(Attribute.<String>named(ATTR_ICON));
					log.debug("Windows: attempting to use icon {}", resName);
					input = urlClassLoader.getResourceAsStream(resName);
				} catch (Throwable ignored) {
					log.info("Windows: icon resource {} can't be opened, omitting", resName);
				}
				if (input != null) {
					try {
						icon = Files.createTempFile("", ".ico");
						log.debug("Windows: copying icon resource to {} and setting launch4j icon", icon.toString());
						Files.copy(input, icon);
						c.setIcon(icon.toFile());
					} finally {
						input.close();
					}
				}
			}

			final Builder builder = new Builder(Log.getConsoleLog(), findOwnJarFile(NativeCapsule.class).toAbsolutePath().getParent().toFile());
			builder.build();

			log.debug("Windows native app build complete");

			return out;
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			if (tmpJar != null)
				Files.delete(tmpJar);
			if (icon != null)
				Files.delete(icon);
		}
	}

	private static String versionToWindowsVersion(String version) {
		for (int count = version.split("\\.").length; count < 4; count++)
			version += ".0";
		return version;
	}

	private static Path setLaunch4JTmpDir() {
		try {
			final Path tmpDir = addTempFile(Files.createTempDirectory("capsule-launch4j-tmp-"));
			log.debug("Creating and setting launch4j temp dir {}", tmpDir.toAbsolutePath().normalize().toString());
			System.setProperty("launch4j.tmpdir", tmpDir.toString());
			return tmpDir;
		} catch (IOException e) {
			throw new RuntimeException("Could not create temporary directory necessary for building a Windows executable", e);
		}
	}

	private static Path setLaunch4JLibDir() {
		try {
			final Path libDir = findOwnJarFile(NativeCapsule.class).toAbsolutePath().getParent().resolve("w32api");
			final String[] linkFiles = new String[] {
				"crt2.o", "libadvapi32.a", "libgcc.a", "libkernel32.a", "libmingw32.a",
				"libmsvcrt.a", "libshell32.a", "libuser32.a"
			};
			log.debug("Copying launch4j w32api linkfiles {} to {}", Arrays.toString(linkFiles), libDir.toAbsolutePath().normalize().toString());

			if (Files.exists(libDir))
				Capsule.delete(libDir);
			addTempFile(Files.createDirectory(libDir));

			for (final String f : linkFiles)
				copy(f, "w32api", libDir);

			return libDir;
		} catch (IOException e) {
			throw new RuntimeException("Could not extract libraries necessary for building a Windows executable", e);
		}
	}

	private static Path setLaunch4JHeadDir() {
		try {
			final Path libDir = findOwnJarFile(NativeCapsule.class).toAbsolutePath().getParent().resolve("head");
			final String[] headFiles = new String[] { "consolehead.o", "guihead.o", "head.o" };
			log.debug("Copying launch4j headers {} to {}", Arrays.toString(headFiles), libDir.toAbsolutePath().normalize().toString());

			if (Files.exists(libDir))
				Capsule.delete(libDir);
			addTempFile(Files.createDirectory(libDir));

			for (final String f : headFiles)
				copy(f, "head", libDir);

			return libDir;
		} catch (IOException e) {
			throw new RuntimeException("Could not extract libraries necessary for building a Windows executable", e);
		}
	}

	private static void setLaunch4JBinDir() {
		if (Platform.myPlatform().isMac())
			copyLaunch4JBins("mac", new String[]{"ld", "windres"});
		else if (Platform.myPlatform().isLinux())
			copyLaunch4JBins("linux", new String[]{"ld", "windres"});
		else if (Platform.myPlatform().isWindows())
			copyLaunch4JBins("windows", new String[]{"ld.exe", "windres.exe"});
		else if (Platform.myPlatform().isUnix())
			log.warn("Detected non-Linux Unix platform, assuming launch4j's 'ld' and 'windres' can be found on the path");
		else
			throw new RuntimeException(Platform.myPlatform() + " is not supported");
	}

	private static Path copyLaunch4JBins(String os, String[] bins) {
		try {
			final Path binDir = addTempFile(Files.createTempDirectory("capsule-launch4j-bin-"));
			log.debug("Copying launch4j binaries {} for platform {} to {} and setting 'launch4j.bindir' system property", Arrays.toString(bins), os, binDir.toAbsolutePath().normalize().toString());
			for (String filename : bins)
				ensureExecutable(copy(filename, "bin/" + os, binDir));
			System.setProperty("launch4j.bindir", binDir.toString());
			return binDir;
		} catch (IOException e) {
			throw new RuntimeException("Could not extract binaries necessary for building a Windows executable", e);
		}
	}

	private static Path copy(String fileName, String resourceDir, Path targetDir) throws IOException {
		log.debug("Copying resource {} to {}", resourceDir + '/' + fileName, targetDir);
		try (InputStream in = NativeCapsule.class.getClassLoader().getResourceAsStream(resourceDir + '/' + fileName);
		     OutputStream out = Files.newOutputStream(targetDir.resolve(fileName))) {
			copy(in, out);
			return targetDir.resolve(fileName);
		}
	}

	private static Path buildUnixApp(Path out) throws IOException {
		log.debug("Building native Unix app: {}", out);

		final Jar jar = createJar(out);
		makeUnixExecutable(jar);
		if (isGUIApp())
			makeGUICapsule(jar);
		jar.close();
		ensureExecutable(out);

		log.debug("Unix native app build complete");

		return out;
	}

	private static Path buildMacApp(Path out) throws IOException {
		out = withSuffix(out, ".app");

		log.debug("Building native Mac OS X app: {}", out);

		Capsule.delete(out);
		Files.createDirectory(out);

		final Path contents = out.resolve("Contents");
		Files.createDirectory(contents);
		try (PrintWriter info = new PrintWriter(Files.newBufferedWriter(contents.resolve("Info.plist"), Charset.forName("UTF-8")))) {
			writeInfo(info);
		}

		final Path resources = contents.resolve("Resources");
		Files.createDirectory(resources);
		final Path macos = contents.resolve("MacOS");
		Files.createDirectory(macos);
		final Path outJarPath = macos.resolve(getSimpleCapsuleName());
		final Jar jar = createJar(outJarPath);
		if (inCapsule.hasAttribute(Attribute.named(ATTR_ICON))) {
			final URLClassLoader urlClassLoader = new URLClassLoader( new URL[] { outJarPath.toUri().toURL() } );
			InputStream input = null;
			String resName = null;
			try {
				resName = inCapsule.getAttribute(Attribute.<String>named(ATTR_ICON)) + ".icns";
				log.debug("Mac OS X: attempting to use icon {}", resName);
				input = urlClassLoader.getResourceAsStream(resName);
			} catch (Throwable ignored) {
				log.info("Mac OS X: icon resource {} can't be opened, omitting", resName);
			}
			if (resName != null && input != null) {
				try {
					final Path iconOut = resources.resolve(resName);
					log.debug("Mac OS X: copying icon resource to {}", iconOut);
					Files.copy(input, iconOut);
				} finally {
					input.close();
				}
			}
		}
		makeUnixExecutable(jar);
		if (isGUIApp())
			makeGUICapsule(jar);
		jar.close();
		ensureExecutable(outJarPath);

		log.debug("Mac OS X native app build complete");

		return out;
	}

	private static void writeInfo(PrintWriter out) {
		out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		out.println("<!DOCTYPE plist PUBLIC \"-//Apple Computer//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">");
		out.println("<plist version=\"1.0\">");
		out.println("<dict>");
		out.println("  <key>CFBundleGetInfoString</key>");
		out.println("  <string>" + getSimpleCapsuleName() + "</string>");
		out.println("  <key>CFBundleExecutable</key>");
		out.println("  <string>" + getSimpleCapsuleName() + "</string>");
		out.println("  <key>CFBundleIdentifier</key>");
		out.println("  <string>" + inCapsule.getAttribute(Attribute.named(Capsule.ATTR_APP_NAME.getKey())) + "</string>");
		out.println("  <key>CFBundleName</key>");
		out.println("  <string>" + inCapsule.getAttribute(Attribute.named(Capsule.ATTR_APP_NAME.getKey())) + "</string>");
		if (inCapsule.hasAttribute(Attribute.named(ATTR_ICON))) {
			out.println("  <key>CFBundleIconFile</key>");
			out.println("  <string>" + inCapsule.getAttribute(Attribute.named(ATTR_ICON)) + "</string>");
		}
		if (inCapsule.hasAttribute(Attribute.named(Capsule.ATTR_APP_VERSION.getKey()))) {
			out.println("  <key>CFBundleShortVersionString</key>");
			out.println("  <string>" + inCapsule.getAttribute(Attribute.named(Capsule.ATTR_APP_VERSION.getKey())) + "</string>");
		}
		out.println("  <key>CFBundleShortVersionString</key>");
		out.println("  <string>1.0</string>");
		out.println("  <key>CFBundleInfoDictionaryVersion</key>");
		out.println("  <string>6.0</string>");
		out.println("  <key>CFBundlePackageType</key>");
		out.println("  <string>APPL</string>");
		out.println("  <key>CFBundleSignature</key>");
		out.println("  <string>????</string>");
		out.println("</dict>");
		out.println("</plist>");
	}

	private static Jar makeUnixExecutable(Jar jar) {
		log.debug("Setting JAR prefix as native Unix executable");
		return jar.setJarPrefix("#!/bin/sh\n\nexec java -jar $0 \"$@\"\n");
	}

	private static Jar makeGUICapsule(Jar jar) throws IOException {
		log.debug("Making a GUI capsule");

		List<String> caplets = inCapsule.getAttribute(Attribute.<List<String>>named(Capsule.ATTR_CAPLETS.getKey()));
		//noinspection Convert2Diamond
		caplets = caplets == null ? new ArrayList<String>() : new ArrayList<String>(caplets);

		log.debug("GUI: adding {}", GUI_CAPSULE_NAME);
		caplets.add(GUI_CAPSULE_NAME);

		boolean usesMaven = false;
		for (Class<?> c : inCapsule.getCaplets()) {
			if (CapletUtil.isSubclass(c, MAVEN_CAPSULE_NAME)) {
				// The 2-stage Capsule lookup/resolve rule is:
				//
				// | Each `lookup` / `resolve`-customizing capsule must return from `lookup` values that only its own
				// | `resolve` can handle. Else (f.e. when extending `MavenCapsule`) the capsule-building process must
				// | make sure that only one of the capsules that can `resolve` the same `lookup` values is present
				// | in the chain.

				log.debug("GUI: removing non-GUI Maven caplet {}", c.getName());
				//noinspection SuspiciousMethodCalls
				caplets.remove(c);
				usesMaven = true;
			}
		}
		if (usesMaven) {
			log.debug("GUI: adding GUI Maven caplet {}", GUI_MAVEN_CAPSULE_NAME);
			caplets.add(GUI_MAVEN_CAPSULE_NAME);
		}

		jar.setListAttribute("Caplets", caplets);

		// jar.addClass(NativeCapsule.class);
		jar.addClass(GUICapsule.class);
		if (inCapsule.hasCaplet(MAVEN_CAPSULE_NAME)) {
			jar.addEntry("GUIMavenCapsule.class", NativeCapsule.class.getResourceAsStream("GUIMavenCapsule.class"));
			jar.addPackageOf(GUIListener.class, Jar.matches("capsule/((GUIDependencyManager)|(GUIListener)).*"));
		}
		return jar;
	}

	private static Path withSuffix(Path path, String suffix) {
		return path.getFileName().toString().endsWith(suffix) ?
			path : path.toAbsolutePath().getParent().resolve(path.getFileName().toString() + suffix);
	}

	private static Path findOwnJarFile(Class clazz) {
		final URL url = clazz.getClassLoader().getResource(clazz.getName().replace('.', '/') + ".class");
		assert url != null;
		if (!"jar".equals(url.getProtocol()))
			throw new AssertionError("Not in JAR");
		final String path = url.getPath();
		if (path == null || !path.startsWith("file:"))
			throw new IllegalStateException("Not in a local JAR file; loaded from: " + url);

		try {
			final URI jarUri = new URI(path.substring(0, path.indexOf('!')));
			return Paths.get(jarUri);
		} catch (URISyntaxException e) {
			throw new AssertionError(e);
		}
	}

	protected static void copy(InputStream is, OutputStream out) throws IOException {
		final byte[] buffer = new byte[1024];
		for (int bytesRead ; (bytesRead = is.read(buffer)) != -1 ; )
			out.write(buffer, 0, bytesRead);
		out.flush();
	}

	private static Path ensureExecutable(Path file) {
		log.debug("Ensuring executable: {}", file.toAbsolutePath().normalize().toString());
		if (!Files.isExecutable(file)) {
			try {
				Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
				if (!perms.contains(PosixFilePermission.OWNER_EXECUTE)) {
					Set<PosixFilePermission> newPerms = EnumSet.copyOf(perms);
					newPerms.add(PosixFilePermission.OWNER_EXECUTE);
					Files.setPosixFilePermissions(file, newPerms);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return file;
	}

	private static Path addTempFile(Path file) {
		log.debug("Adding temp file: {}", file.toAbsolutePath().normalize().toString());
		tmpFiles.add(file);
		return file;
	}
}
