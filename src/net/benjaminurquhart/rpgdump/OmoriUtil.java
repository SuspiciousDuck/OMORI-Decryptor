package net.benjaminurquhart.rpgdump;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JProgressBar;

import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class OmoriUtil {
	
	// These are key hashes, to verify that the key is correct
	// The decryption key for OMORI 1.0.0 appears to be detected automatically
	// The decryption keys for further versions are found in OMORI's launch options-
	// - which you can find here: https://steamdb.info/app/1150690/config
	public static final String OLD_KEY_HASH = "06494167914dab96fc9e58b5e2ee9eb98ad230edd6048d2134b8e18a0726f7c4";
	public static final String KEY_HASH = "b1d50d2686248fc493b71cd490cb88ac75e71caff236fdb4ab9fa78a36319e11";
	
	public static final Pattern KEY_PATTERN = Pattern.compile("\\-\\-([0-9a-f]{32})");
	
	private static File folder;
	private static String decryptionKey;
	
	public static void init(File folder, String decryptionKey) throws Exception {
		JProgressBar progressBar = UI.getInstance().progressBar;
		progressBar.setIndeterminate(true);
		progressBar.setString("Initializing decryptor...");
		
		String hash = hash(decryptionKey);
		
		// Check for 1.0.0
		if(!hash.equals(OLD_KEY_HASH)) {
			File mainjs = new File(RPGMakerUtil.getRootAssetFolder(), "js/main.js");
			
			if(mainjs.exists()) {
				String js = Files.lines(mainjs.toPath()).collect(Collectors.joining("\n"));
				if(js.contains("let key=")) {
					decryptionKey = js.split("let key='")[1].split("';", 2)[0];
					
					if(hash(decryptionKey).equals(OLD_KEY_HASH)) {
						System.out.println("OMORI 1.0.0 decryption key found");
						OmoriUtil.decryptionKey = decryptionKey;
						OmoriUtil.folder = folder;
						return;
					}
					else {
						throw new IllegalStateException("Invalid OMORI 1.0.0 decryption key?");
					}
				}
			}
		}
		
		if(!hash.equals(KEY_HASH)) {			
			// --6bdb2e585882fbd48826ef9cffd4c511 is v1.0.8's launch option
			// which means that 6bdb2e585882fbd48826ef9cffd4c511 is the decryption key
			System.out.println("Found decryption key.");
			decryptionKey = "6bdb2e585882fbd48826ef9cffd4c511";
		}
		
		OmoriUtil.decryptionKey = decryptionKey;
		OmoriUtil.folder = folder;
	}
	
	private static List<ZipEntry> getZipEntriesWithExts(ZipFile file, String... exts) {
		return file.stream()
				   .filter(entry -> !entry.isDirectory())
				   .filter(entry -> {
					   for(String ext : exts) {
						   if(entry.getName().endsWith(ext)) {
							   return true;
						   }
					   }
					   return false;
				   }).collect(Collectors.toList());
	}
	
	public static Set<String> getDetectedMods() {
		Set<String> out = new HashSet<>(), seen = new HashSet<>();
		File modFolder = new File(RPGMakerUtil.getRootAssetFolder(), "mods");
		
		if(modFolder.exists()) {
			List<File> looseMods = Main.getFilesWithExts(modFolder, null, "mod.json");
			List<File> compressedMods = Main.getFilesWithExts(modFolder, null, ".zip");
			
			List<ZipEntry> compressedEntries;
			
			StringBuilder sb = new StringBuilder();
			InputStream stream;
			JSONObject config;
			String json;
			int b;
			
			for(File mod : looseMods) {
				System.out.println(mod.getAbsolutePath());
				try {
					json = Main.readString(mod);
					//System.out.println(json);
					config = new JSONObject(json);
					if(seen.add(config.optString("id"))) {
						out.add(String.format("%s v%s (%s)", config.optString("name", mod.getName()), config.optString("version", "???"), config.optString("id", mod.getName())));
					}
				}
				catch(Exception e) {
					e.printStackTrace(System.out);
				}
			}
			
			for(File mod : compressedMods) {
				try(ZipFile zip = new ZipFile(mod)) {
					compressedEntries = getZipEntriesWithExts(zip, "mod.json");
					for(ZipEntry entry : compressedEntries) {
						System.out.println(mod.getAbsolutePath() + ":" + entry.getName());
						sb.delete(0, sb.length());
						stream = zip.getInputStream(entry);
						while((b = stream.read()) != -1) {
							sb.append((char)b);
						}
						stream.close();
						//System.out.println(sb);
						config = new JSONObject(sb.toString());
						if(seen.add(config.optString("id"))) {
							out.add(String.format("%s v%s (%s)", config.optString("name", mod.getName()), config.optString("version", "???"), config.optString("id", mod.getName())));
						}
					}
				}
				catch(Exception e) {
					e.printStackTrace(System.out);
				}
			}
		}
		return out;
	}

	public static void decrypt() throws Exception {
		JProgressBar progressBar = UI.getInstance().progressBar;
		progressBar.setString("Decrypting");
		
		if(decryptionKey != null) {
			System.out.println("Finding files to decrypt...");
			List<File> files = Main.getFilesWithExts(folder, RPGMakerUtil.defaultExclusions, ".OMORI", ".AUBREY", ".PLUTO", ".HERO", ".KEL");
			if(files.isEmpty()) {
				System.out.println("Nothing found!");
			}
			else {
				FileOutputStream stream;
				ByteArrayOutputStream decrypted = new ByteArrayOutputStream();
				
				byte[] secret = decryptionKey.getBytes("utf-8"), output, iv, bytes;
				SecretKeySpec spec = new SecretKeySpec(secret, "AES");
				Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
				
				ObjectMapper mapper;
				File normal, backup;
				String tmpJson;
				
				int index = 1;
				for(File file : files) {
					try {
						Main.updateProgressBar("Decrypting", file.getName(), index++, files.size());
						backup = new File(file.getName() + ".BASIL");
						bytes = Files.readAllBytes(backup.exists() ? backup.toPath() : file.toPath());
						
						iv = Arrays.copyOfRange(bytes, 0, 16);
						bytes = Arrays.copyOfRange(bytes, 16, bytes.length);
						
						cipher.init(Cipher.DECRYPT_MODE, spec, new IvParameterSpec(iv));
						
						normal = Main.getNormalFile(file);
						decrypted.reset();
						
						output = cipher.update(bytes);
						if(output != null) {
							decrypted.write(output);
						}
						decrypted.write(cipher.doFinal());
						
						stream = new FileOutputStream(normal);
						
						if(normal.getName().endsWith(".json")) {
							try {
								mapper = new ObjectMapper();
								mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
								
								tmpJson = new String(decrypted.toByteArray(), "utf-8");
								tmpJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readValue(tmpJson, Object.class));
								
								stream.write(tmpJson.getBytes("utf-8"));
							}
							catch(Exception e) {
								System.out.println("\u001b[1000D\u001b[2KFailed to beautify json: " + e);
								stream.write(decrypted.toByteArray());
							}
						}
						else {
							stream.write(decrypted.toByteArray());
						}
						stream.flush();
						stream.close();
					}
					catch(Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		else {
			throw new IllegalStateException("Decryption key not found");
		}
	}

	
	private static String hash(String string) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("sha-256");
		byte[] hash = digest.digest(String.valueOf(string).getBytes("utf-8"));
		return Main.bytesToHexString(hash);
	}
	

}
