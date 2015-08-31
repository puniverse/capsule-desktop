/*
 * Copyright (c) 2014-2015, Parallel Universe Software Co. and Contributors. All rights reserved.
 *
 * This program and the accompanying materials are licensed under the terms
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import capsule.GUIListener;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import static java.util.Arrays.*;

/**
 * Wrapping capsule that will build and launch a native desktop GUI or non-GUI app
 */
public class NativeCapsule {
	// private static final String PROP_VERSION = OPTION("capsule.build", "false", "build", true, "Builds the native application.");

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

	private static List<Path> tmpFiles = new ArrayList<>();
	private static Path inCapsulePath;
	private static String outCapsuleBasePath;
	private static co.paralleluniverse.capsule.Capsule inCapsule;
	private static boolean buildMac, buildLinux, buildWindows;

	public static void main(String[] args) throws IOException {
		final OptionParser parser = new OptionParser();
		final OptionSpec<String> c = parser.acceptsAll(asList("c", "capsule")).withRequiredArg().ofType(String.class).describedAs("A single capsule pathname to build native binaries for");
		final OptionSpec<String> o = parser.acceptsAll(asList("o", "output")).withRequiredArg().ofType(String.class).describedAs("The base output pathname of built binaries");
		parser.acceptsAll(asList("m", "macosx"), "Build Mac OS X binary");
		parser.acceptsAll(asList("l", "linux"), "Build Linux binary");
		parser.acceptsAll(asList("w", "windows"), "Build Windows binary");
		parser.acceptsAll(asList("h", "?", "help"), "Show help").forHelp();
		final OptionSet options = parser.parse(args);

		if (!options.has(c) || options.valuesOf(c).size() != 1 || options.valuesOf(o).size() > 1) {
			parser.printHelpOn(System.err);
			System.exit(-1);
		}

		inCapsulePath = Paths.get(options.valuesOf(c).get(0));
		inCapsule = new CapsuleLauncher(inCapsulePath).newCapsule();
		outCapsuleBasePath = options.valuesOf(o).size() == 1 ? options.valuesOf(o).get(0) : getOutputBase();
		buildMac = options.has("m") || options.has("macosx");
		buildLinux = options.has("l") || options.has("linux");
		buildWindows = options.has("w") || options.has("windows");

		buildNative();
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
		final String outBase = getOutputBase(outCapsuleBasePath);
		try {
			final List<String> platforms = new ArrayList<>();
			if (buildMac)
				platforms.add("macos");
			if (buildLinux)
				platforms.add("linux");
			if (buildWindows)
				platforms.add("windows");

			if (platforms.isEmpty())
				platforms.add("CURRENT"); // Default

			for (final String p : platforms)
				buildApp(p, Paths.get(outBase));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void buildApp(String platform, Path out) throws IOException {
		if ("macos".equals(platform))
			buildMacApp(out);
		else if ("linux".equals(platform))
			buildLinuxApp(out);
		else if ("windows".equals(platform))
			buildWindowsApp(out);
		else if ("CURRENT".equals(platform))
			buildApp(Platform.myPlatform().getOS(), out);
		else
			throw new RuntimeException(("Platform \"" + platform + "\" is unsupported"));
	}

	private static String getSimpleCapsuleName() {
		final String filename = inCapsulePath.getFileName().toString();
		return filename.endsWith(".jar") ? filename.substring(0, filename.length() - 4) : filename;
	}

	private static Jar createJar(Path out) throws IOException {
		final Jar jar = new Jar(inCapsulePath);
		if (out != null)
			jar.setOutput(out);
		return jar;
	}

	private static boolean isGUIApp() {
		if (inCapsule.hasAttribute(Attribute.<Boolean>named(ATTR_GUI)))
			return inCapsule.getAttribute(Attribute.<Boolean>named(ATTR_GUI));
		return false;
	}

	private static Path buildWindowsApp(Path out) throws IOException {
		setLaunch4JBinDir();
		setLaunch4JLibDir();
		setLaunch4JHeadDir();
		setLaunch4JTmpDir();

		Path tmpJar = null;
		Path icon = null;
		try {
			if (isGUIApp()) {
				tmpJar = Files.createTempFile("native-capsule-", ".jar");
				Jar j = createJar(tmpJar);
				makeGUICapsule(j);
				j.close();
			}
			final Path jar = tmpJar != null ? tmpJar : inCapsulePath;

			ConfigPersister.getInstance().createBlank();
			final Config c = ConfigPersister.getInstance().getConfig();
			c.setHeaderType(isGUIApp() ? Config.GUI_HEADER : Config.CONSOLE_HEADER);
			c.setOutfile(withSuffix(out, ".exe").toFile());
			c.setJar(jar.toFile());

			if (inCapsule.hasAttribute(Attribute.named(Capsule.ATTR_MIN_JAVA_VERSION.getKey())))
				c.getJre().setMinVersion(inCapsule.getAttribute(Attribute.<String>named(Capsule.ATTR_MIN_JAVA_VERSION.getKey())));
			if (inCapsule.hasAttribute(Attribute.named(Capsule.ATTR_JAVA_VERSION.getKey())))
				c.getJre().setMaxVersion(inCapsule.getAttribute(Attribute.<String>named(Capsule.ATTR_JAVA_VERSION.getKey())));
			if (inCapsule.hasAttribute(Attribute.named(Capsule.ATTR_JDK_REQUIRED.getKey())))
				c.getJre().setJdkPreference(inCapsule.<Boolean>getAttribute(Attribute.<Boolean>named(Capsule.ATTR_JDK_REQUIRED.getKey())) ? Jre.JDK_PREFERENCE_JDK_ONLY : null);

			if (inCapsule.hasAttribute(Attribute.<Boolean>named(ATTR_SINGLE_INSTANCE)) && inCapsule.getAttribute(Attribute.<Boolean>named(ATTR_SINGLE_INSTANCE))) {
				final SingleInstance si = new SingleInstance();
				si.setWindowTitle(inCapsule.getAttribute(Attribute.<String>named(Capsule.ATTR_APP_NAME.getKey())));
				si.setMutexName(inCapsule.getAppId());
				c.setSingleInstance(si);
			}

			if (inCapsule.getAttribute(Attribute.<String>named(ATTR_IMPLEMENTATION_VENDOR)) != null
				|| inCapsule.getAttribute(Attribute.<String>named(ATTR_NATIVE_DESCRIPTION)) != null
				|| inCapsule.getAttribute(Attribute.<String>named(ATTR_COPYRIGHT)) != null
				|| inCapsule.getAttribute(Attribute.<String>named(ATTR_INTERNAL_NAME)) != null) {

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
				try {
					input = urlClassLoader.getResourceAsStream(inCapsule.getAttribute(Attribute.<String>named(ATTR_ICON)));
				} catch (Throwable ignored) {}
				if (input != null) {
					try {
						icon = Files.createTempFile("", ".ico");
						Files.copy(input, icon);
						c.setIcon(icon.toFile());
					} finally {
						input.close();
					}
				}
			}

			final Builder builder = new Builder(Log.getConsoleLog(), findOwnJarFile(NativeCapsule.class).toAbsolutePath().getParent().toFile());
			builder.build();
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
			final Path tmpdir = addTempFile(Files.createTempDirectory("capsule-launch4j-tmp-"));
			System.setProperty("launch4j.tmpdir", tmpdir.toString());
			return tmpdir;
		} catch (IOException e) {
			throw new RuntimeException("Could not create temporary directory necessary for building a Windows executable", e);
		}
	}

	private static Path setLaunch4JLibDir() {
		try {
			final Path libdir = findOwnJarFile(NativeCapsule.class).toAbsolutePath().getParent().resolve("w32api");
			if (Files.exists(libdir))
				Capsule.delete(libdir);
			addTempFile(Files.createDirectory(libdir));

			for (String filename : new String[]{
				"crt2.o", "libadvapi32.a", "libgcc.a", "libkernel32.a", "libmingw32.a",
				"libmsvcrt.a", "libshell32.a", "libuser32.a"})
				copy(filename, "w32api", libdir);

			return libdir;
		} catch (IOException e) {
			throw new RuntimeException("Could not extract libraries necessary for building a Windows executable", e);
		}
	}

	private static Path setLaunch4JHeadDir() {
		try {
			final Path libdir = findOwnJarFile(NativeCapsule.class).toAbsolutePath().getParent().resolve("head");
			if (Files.exists(libdir))
				Capsule.delete(libdir);
			addTempFile(Files.createDirectory(libdir));

			for (final String filename : new String[]{"consolehead.o", "guihead.o", "head.o"})
				copy(filename, "head", libdir);

			return libdir;
		} catch (IOException e) {
			throw new RuntimeException("Could not extract libraries necessary for building a Windows executable", e);
		}
	}

	private static void setLaunch4JBinDir() {
		if (Platform.myPlatform().isMac())
			copyBin("mac", new String[]{"ld", "windres"});
		else if (Platform.myPlatform().toString().contains("linux")) // TODO Expand Platform a bit
			copyBin("linux", new String[]{"ld", "windres"});
		else if (Platform.myPlatform().isWindows())
			copyBin("windows", new String[]{"ld.exe", "windres.exe"});
		else
			throw new RuntimeException(Platform.myPlatform() + " is not supported");
	}

	private static Path copyBin(String os, String[] bins) {
		try {
			final Path bindir = addTempFile(Files.createTempDirectory("capsule-launch4j-bin-"));
			for (String filename : bins)
				ensureExecutable(copy(filename, "bin/" + os, bindir));
			System.setProperty("launch4j.bindir", bindir.toString());
			return bindir;
		} catch (IOException e) {
			throw new RuntimeException("Could not extract binaries necessary for building a Windows executable", e);
		}
	}

	private static Path copy(String filename, String resourceDir, Path targetDir) throws IOException {
		try (InputStream in = NativeCapsule.class.getClassLoader().getResourceAsStream(resourceDir + '/' + filename);
		     OutputStream out = Files.newOutputStream(targetDir.resolve(filename))) {
			copy(in, out);
			return targetDir.resolve(filename);
		}
	}

	private static Path buildLinuxApp(Path out) throws IOException {
		final Jar jar = createJar(out);
		makeUnixExecutable(jar);
		if (isGUIApp())
			makeGUICapsule(jar);
		jar.close();
		ensureExecutable(out);
		return out;
	}

	private static Path buildMacApp(Path out) throws IOException {
		out = withSuffix(out, ".app");
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
				input = urlClassLoader.getResourceAsStream(resName);
			} catch (Throwable ignored) {}
			if (resName != null && input != null) {
				try {
					Files.copy(input, resources.resolve(resName));
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
		return jar.setJarPrefix("#!/bin/sh\n\nexec java -jar $0 \"$@\"\n");
	}

	private static Jar makeGUICapsule(Jar jar) throws IOException {
		List<String> caplets = inCapsule.getAttribute(Attribute.<List<String>>named(Capsule.ATTR_CAPLETS.getKey()));
		//noinspection Convert2Diamond
		caplets = caplets == null ? new ArrayList<String>() : new ArrayList<String>(caplets);

		// caplets.add(NativeCapsule.class.getName());
		caplets.add(GUI_CAPSULE_NAME);
		if (inCapsule.hasCaplet(MAVEN_CAPSULE_NAME)) {
			caplets.remove(MAVEN_CAPSULE_NAME); // Dependency resolution conflicts between them
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
		return path.getFileName().toString().endsWith(suffix) ? path : path.toAbsolutePath().getParent().resolve(path.getFileName().toString() + suffix);
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
		for (int bytesRead; (bytesRead = is.read(buffer)) != -1; )
			out.write(buffer, 0, bytesRead);
		out.flush();
	}

	private static Path ensureExecutable(Path file) {
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

	public static Path addTempFile(Path file) {
		tmpFiles.add(file);
		return file;
	}
}
