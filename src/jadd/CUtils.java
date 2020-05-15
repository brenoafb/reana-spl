package jadd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bridj.DynamicFunction;
import org.bridj.NativeLibrary;
import org.bridj.Pointer;

class CUtils {
	private static final Logger LOGGER = Logger.getLogger(CUtils.class.getName());

	private static NativeLibrary libc;
	private static DynamicFunction<Pointer<?>> fopenHandle;
	private static DynamicFunction<?> fcloseHandle;

	public static final String ACCESS_WRITE = "w";

	static {
		try {
			String path;
			try {
				path = getLibcPath();
			} catch (InterruptedException | IOException e) {
				path = "/lib/x86_64-linux-gnu/libc.so.6";
			}

			libc = NativeLibrary.load(path);

			Pointer<?> fopenAddress = libc.getSymbolPointer("fopen");
			fopenHandle = fopenAddress.asDynamicFunction(null, Pointer.class, Pointer.class, Pointer.class);

			Pointer<?> fcloseAddress = libc.getSymbolPointer("fclose");
			fcloseHandle = fcloseAddress.asDynamicFunction(null, int.class, Pointer.class);
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.toString(), e);
		}
	}

	public static Pointer<?> fopen(String fileName, String access) {
		return fopenHandle.apply(Pointer.pointerToCString(fileName),
				Pointer.pointerToCString(access));
	}

	public static int fclose(Pointer<?> openFile) {
		return (int) fcloseHandle.apply(openFile);
	}

	private static String getLibcPath() throws IOException, InterruptedException {
		Runtime rt = Runtime.getRuntime();
		Process pr = rt.exec("gcc --print-file-name=libc.so.6");

		BufferedReader stdInput = new BufferedReader(new InputStreamReader(pr.getInputStream()));
		String path = stdInput.readLine();
		int exitCode = pr.waitFor();

		if (exitCode != 0) {
			throw new IOException("Error getting library path from gcc.");
		}

		return path;
	}
}
