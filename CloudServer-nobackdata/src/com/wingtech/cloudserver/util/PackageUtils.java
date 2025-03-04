package com.wingtech.cloudserver.util;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;


import java.io.File;
import java.util.List;


public class PackageUtils {

	public static final String TAG = "PackageUtils";

	public static int installSilent(Context context, String filePath,
									String pmParams) {
		
		if (filePath == null || filePath.length() == 0) {
			return INSTALL_FAILED_INVALID_URI;
		}

		File file = new File(filePath);
		
		if (file == null || file.length() <= 0 || !file.exists()
				|| !file.isFile()) {
			return INSTALL_FAILED_INVALID_URI;
		}

		String pkgName = context.getPackageName();

		StringBuilder command = new StringBuilder()
				.append("LD_LIBRARY_PATH=/vendor/lib*:/system/lib* pm install ")
				.append(pmParams).append(" ").append(pkgName).append(" --user 0 ")
				.append(filePath.replace(" ", "\\ "));
		
		ShellUtils.CommandResult commandResult = ShellUtils.execCommand(
				command.toString(), !isSystemApplication(context), true);
		if (commandResult.successMsg != null
				&& (commandResult.successMsg.contains("Success") || commandResult.successMsg
				.contains("success"))) {
			return INSTALL_SUCCEEDED;
		}

		
		if (commandResult.errorMsg == null) {
			return INSTALL_FAILED_OTHER;
		}
		if (commandResult.errorMsg.contains("INSTALL_FAILED_ALREADY_EXISTS")) {
			return INSTALL_FAILED_ALREADY_EXISTS;
		}
		if (commandResult.errorMsg.contains("INSTALL_FAILED_INVALID_APK")) {
			return INSTALL_FAILED_INVALID_APK;
		}
		if (commandResult.errorMsg.contains("INSTALL_FAILED_INVALID_URI")) {
			return INSTALL_FAILED_INVALID_URI;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE")) {
			return INSTALL_FAILED_INSUFFICIENT_STORAGE;
		}
		if (commandResult.errorMsg.contains("INSTALL_FAILED_DUPLICATE_PACKAGE")) {
			return INSTALL_FAILED_DUPLICATE_PACKAGE;
		}
		if (commandResult.errorMsg.contains("INSTALL_FAILED_NO_SHARED_USER")) {
			return INSTALL_FAILED_NO_SHARED_USER;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE")) {
			return INSTALL_FAILED_UPDATE_INCOMPATIBLE;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_FAILED_SHARED_USER_INCOMPATIBLE")) {
			return INSTALL_FAILED_SHARED_USER_INCOMPATIBLE;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_FAILED_MISSING_SHARED_LIBRARY")) {
			return INSTALL_FAILED_MISSING_SHARED_LIBRARY;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_FAILED_REPLACE_COULDNT_DELETE")) {
			return INSTALL_FAILED_REPLACE_COULDNT_DELETE;
		}
		if (commandResult.errorMsg.contains("INSTALL_FAILED_DEXOPT")) {
			return INSTALL_FAILED_DEXOPT;
		}
		if (commandResult.errorMsg.contains("INSTALL_FAILED_OLDER_SDK")) {
			return INSTALL_FAILED_OLDER_SDK;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_FAILED_CONFLICTING_PROVIDER")) {
			return INSTALL_FAILED_CONFLICTING_PROVIDER;
		}
		if (commandResult.errorMsg.contains("INSTALL_FAILED_NEWER_SDK")) {
			return INSTALL_FAILED_NEWER_SDK;
		}
		if (commandResult.errorMsg.contains("INSTALL_FAILED_TEST_ONLY")) {
			return INSTALL_FAILED_TEST_ONLY;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_FAILED_CPU_ABI_INCOMPATIBLE")) {
			return INSTALL_FAILED_CPU_ABI_INCOMPATIBLE;
		}
		if (commandResult.errorMsg.contains("INSTALL_FAILED_MISSING_FEATURE")) {
			return INSTALL_FAILED_MISSING_FEATURE;
		}
		if (commandResult.errorMsg.contains("INSTALL_FAILED_CONTAINER_ERROR")) {
			return INSTALL_FAILED_CONTAINER_ERROR;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_FAILED_INVALID_INSTALL_LOCATION")) {
			return INSTALL_FAILED_INVALID_INSTALL_LOCATION;
		}
		if (commandResult.errorMsg.contains("INSTALL_FAILED_MEDIA_UNAVAILABLE")) {
			return INSTALL_FAILED_MEDIA_UNAVAILABLE;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_FAILED_VERIFICATION_TIMEOUT")) {
			return INSTALL_FAILED_VERIFICATION_TIMEOUT;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_FAILED_VERIFICATION_FAILURE")) {
			return INSTALL_FAILED_VERIFICATION_FAILURE;
		}
		if (commandResult.errorMsg.contains("INSTALL_FAILED_PACKAGE_CHANGED")) {
			return INSTALL_FAILED_PACKAGE_CHANGED;
		}
		if (commandResult.errorMsg.contains("INSTALL_FAILED_UID_CHANGED")) {
			return INSTALL_FAILED_UID_CHANGED;
		}
		if (commandResult.errorMsg.contains("INSTALL_PARSE_FAILED_NOT_APK")) {
			return INSTALL_PARSE_FAILED_NOT_APK;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_PARSE_FAILED_BAD_MANIFEST")) {
			return INSTALL_PARSE_FAILED_BAD_MANIFEST;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION")) {
			return INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_PARSE_FAILED_NO_CERTIFICATES")) {
			return INSTALL_PARSE_FAILED_NO_CERTIFICATES;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES")) {
			return INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_PARSE_FAILED_CERTIFICATE_ENCODING")) {
			return INSTALL_PARSE_FAILED_CERTIFICATE_ENCODING;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME")) {
			return INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_PARSE_FAILED_BAD_SHARED_USER_ID")) {
			return INSTALL_PARSE_FAILED_BAD_SHARED_USER_ID;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_PARSE_FAILED_MANIFEST_MALFORMED")) {
			return INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
		}
		if (commandResult.errorMsg
				.contains("INSTALL_PARSE_FAILED_MANIFEST_EMPTY")) {
			return INSTALL_PARSE_FAILED_MANIFEST_EMPTY;
		}
		if (commandResult.errorMsg.contains("INSTALL_FAILED_INTERNAL_ERROR")) {
			return INSTALL_FAILED_INTERNAL_ERROR;
		}
		return INSTALL_FAILED_OTHER;
	}

	/**
	 * uninstall package silent by root
	 * <ul>
	 * <strong>Attentions:</strong>
	 * <li>Don't call this on the ui thread, it may costs some times.</li>
	 * <li>You should add <strong>android.permission.DELETE_PACKAGES</strong> in
	 * manifest, so no need to request root permission, if you are system app.</li>
	 * </ul>
	 *
	 * @ * @ @ * @ <li>@ * <li>@ * <li>@ * <li>@
	 */
	public static int uninstallSilent(Context context, String packageName,
									  boolean isKeepData) {
		
		if (packageName == null || packageName.length() == 0) {
			return DELETE_FAILED_INVALID_PACKAGE;
		}

		/**
		 * if context is system app, don't need root permission, but should add
		 * <uses-permission android:name="android.permission.DELETE_PACKAGES" />
		 * in mainfest
		 **/
		StringBuilder command = new StringBuilder()
				.append("LD_LIBRARY_PATH=/vendor/lib*:/system/lib* pm uninstall")
				.append(isKeepData ? " -k " : " ").append("--user 0 ")
				.append(packageName.replace(" ", "\\ "));

		Log.d(TAG, "uninstallSilent: uninstallCommand="+command);
		
		ShellUtils.CommandResult commandResult = ShellUtils.execCommand(
				command.toString(), !isSystemApplication(context), true);

		if (commandResult.successMsg != null
				&& (commandResult.successMsg.contains("Success") || commandResult.successMsg
				.contains("success"))) {
			return DELETE_SUCCEEDED;
		}
		Log.d(TAG, "uninstallSilent: commandResult.successMsg="+commandResult.successMsg);
		if (commandResult.errorMsg == null) {
			return DELETE_FAILED_INTERNAL_ERROR;
		}
		Log.d(TAG, "uninstallSilent: commandResult.errorMsg="+commandResult.errorMsg);
		if (commandResult.errorMsg.contains("Permission denied")) {
			return DELETE_FAILED_PERMISSION_DENIED;
		}
		return DELETE_FAILED_INTERNAL_ERROR;
	}

	/**
	 * whether context is system application
	 *
	 * @ * @
	 */
	public static boolean isSystemApplication(Context context) {
		if (context == null) {
			return false;
		}

		return isSystemApplication(context, context.getPackageName());
	}

	/**
	 * whether packageName is system application
	 *
	 * @ * @ @
	 */
	public static boolean isSystemApplication(Context context,
											  String packageName) {
		if (context == null) {
			return false;
		}

		return isSystemApplication(context.getPackageManager(), packageName);
	}

	/**
	 * whether packageName is system application
	 *
	 * @ * @ @ * <li>if packageManager is null, return false</li> <li>if package
	 * name is null or is empty, return false</li> <li>if package name not exit,
	 * return false</li> <li>if package name exit, but not system app, return
	 * false</li> <li>else return true</li> </ul>
	 */
	public static boolean isSystemApplication(PackageManager packageManager,
											  String packageName) {
		if (packageManager == null || packageName == null
				|| packageName.length() == 0) {
			return false;
		}

		try {
			ApplicationInfo app = packageManager.getApplicationInfo(
					packageName, 0);
			return (app != null && (app.flags & ApplicationInfo.FLAG_SYSTEM) > 0);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * whether the app whost package's name is packageName is on the top of the
	 * stack
	 * <ul>
	 * <strong>Attentions:</strong>
	 * <li>You should add <strong>android.permission.GET_TASKS</strong> in
	 * manifest</li>
	 * </ul>
	 *
	 * @ * @ @ * stack
	 */
	public static Boolean isTopActivity(Context context, String packageName) {
		if (context == null
				|| (packageName == null || packageName.length() == 0)) {
			return null;
		}

		ActivityManager activityManager = (ActivityManager) context
				.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningTaskInfo> tasksInfo = activityManager.getRunningTasks(1);
		if ((tasksInfo == null || tasksInfo.size() == 0)) {
			return null;
		}
		try {
			return packageName.equals(tasksInfo.get(0).topActivity
					.getPackageName());
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * get app version code
	 *
	 * @ * @
	 */
	public static int getAppVersionCode(Context context) {
		if (context != null) {
			PackageManager pm = context.getPackageManager();
			if (pm != null) {
				PackageInfo pi;
				try {
					pi = pm.getPackageInfo(context.getPackageName(), 0);
					if (pi != null) {
						return pi.versionCode;
					}
				} catch (NameNotFoundException e) {
					e.printStackTrace();
				}
			}
		}
		return -1;
	}

	/**
	 * start InstalledAppDetails Activity
	 *
	 * @ * @
	 */
	public static void startInstalledAppDetails(Context context,
												String packageName) {
		Intent intent = new Intent();
		int sdkVersion = Build.VERSION.SDK_INT;
		if (sdkVersion >= 9) {
			intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
			intent.setData(Uri.fromParts("package", packageName, null));
		} else {
			intent.setAction(Intent.ACTION_VIEW);
			intent.setClassName("com.android.settings",
					"com.android.settings.InstalledAppDetails");
			intent.putExtra((sdkVersion == 8 ? "pkg"
					: "com.android.settings.ApplicationPkgName"), packageName);
		}
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(intent);
	}

	/**
	 * Installation return code<br/>
	 * install success.
	 */
	public static final int INSTALL_SUCCEEDED = 1;
	/**
	 * Installation return code<br/>
	 * the package is already installed.
	 */
	public static final int INSTALL_FAILED_ALREADY_EXISTS = -1;

	/**
	 * Installation return code<br/>
	 * the package archive file is invalid.
	 */
	public static final int INSTALL_FAILED_INVALID_APK = -2;

	/**
	 * Installation return code<br/>
	 * the URI passed in is invalid.
	 */
	public static final int INSTALL_FAILED_INVALID_URI = -3;

	/**
	 * Installation return code<br/>
	 * the package manager service found that the device didn't have enough
	 * storage space to install the app.
	 */
	public static final int INSTALL_FAILED_INSUFFICIENT_STORAGE = -4;

	/**
	 * Installation return code<br/>
	 * a package is already installed with the same name.
	 */
	public static final int INSTALL_FAILED_DUPLICATE_PACKAGE = -5;

	/**
	 * Installation return code<br/>
	 * the requested shared user does not exist.
	 */
	public static final int INSTALL_FAILED_NO_SHARED_USER = -6;

	/**
	 * Installation return code<br/>
	 * a previously installed package of the same name has a different signature
	 * than the new package (and the old package's data was not removed).
	 */
	public static final int INSTALL_FAILED_UPDATE_INCOMPATIBLE = -7;

	/**
	 * Installation return code<br/>
	 * the new package is requested a shared user which is already installed on
	 * the device and does not have matching signature.
	 */
	public static final int INSTALL_FAILED_SHARED_USER_INCOMPATIBLE = -8;

	/**
	 * Installation return code<br/>
	 * the new package uses a shared library that is not available.
	 */
	public static final int INSTALL_FAILED_MISSING_SHARED_LIBRARY = -9;

	/**
	 * Installation return code<br/>
	 * the new package uses a shared library that is not available.
	 */
	public static final int INSTALL_FAILED_REPLACE_COULDNT_DELETE = -10;

	/**
	 * Installation return code<br/>
	 * the new package failed while optimizing and validating its dex files,
	 * either because there was not enough storage or the validation failed.
	 */
	public static final int INSTALL_FAILED_DEXOPT = -11;

	/**
	 * Installation return code<br/>
	 * the new package failed because the current SDK version is older than that
	 * required by the package.
	 */
	public static final int INSTALL_FAILED_OLDER_SDK = -12;

	/**
	 * Installation return code<br/>
	 * the new package failed because it contains a content provider with the
	 * same authority as a provider already installed in the system.
	 */
	public static final int INSTALL_FAILED_CONFLICTING_PROVIDER = -13;

	/**
	 * Installation return code<br/>
	 * the new package failed because the current SDK version is newer than that
	 * required by the package.
	 */
	public static final int INSTALL_FAILED_NEWER_SDK = -14;

	/**
	 * Installation return code<br/>
	 * the new package failed because it has specified that it is a test-only
	 * package and the caller has not supplied the @
	 */
	public static final int INSTALL_FAILED_TEST_ONLY = -15;

	/**
	 * Installation return code<br/>
	 * the package being installed contains native code, but none that is
	 * compatible with the the device's CPU_ABI.
	 */
	public static final int INSTALL_FAILED_CPU_ABI_INCOMPATIBLE = -16;

	/**
	 * Installation return code<br/>
	 * the new package uses a feature that is not available.
	 */
	public static final int INSTALL_FAILED_MISSING_FEATURE = -17;

	/**
	 * Installation return code<br/>
	 * a secure container mount point couldn't be accessed on external media.
	 */
	public static final int INSTALL_FAILED_CONTAINER_ERROR = -18;

	/**
	 * Installation return code<br/>
	 * the new package couldn't be installed in the specified install location.
	 */
	public static final int INSTALL_FAILED_INVALID_INSTALL_LOCATION = -19;

	/**
	 * Installation return code<br/>
	 * the new package couldn't be installed in the specified install location
	 * because the media is not available.
	 */
	public static final int INSTALL_FAILED_MEDIA_UNAVAILABLE = -20;

	/**
	 * Installation return code<br/>
	 * the new package couldn't be installed because the verification timed out.
	 */
	public static final int INSTALL_FAILED_VERIFICATION_TIMEOUT = -21;

	/**
	 * Installation return code<br/>
	 * the new package couldn't be installed because the verification did not
	 * succeed.
	 */
	public static final int INSTALL_FAILED_VERIFICATION_FAILURE = -22;

	/**
	 * Installation return code<br/>
	 * the package changed from what the calling program expected.
	 */
	public static final int INSTALL_FAILED_PACKAGE_CHANGED = -23;

	/**
	 * Installation return code<br/>
	 * the new package is assigned a different UID than it previously held.
	 */
	public static final int INSTALL_FAILED_UID_CHANGED = -24;

	/**
	 * Installation return code<br/>
	 * if the parser was given a path that is not a file, or does not end with
	 * the expected '.apk' extension.
	 */
	public static final int INSTALL_PARSE_FAILED_NOT_APK = -100;

	/**
	 * Installation return code<br/>
	 * if the parser was unable to retrieve the AndroidManifest.xml file.
	 */
	public static final int INSTALL_PARSE_FAILED_BAD_MANIFEST = -101;

	/**
	 * Installation return code<br/>
	 * if the parser encountered an unexpected exception.
	 */
	public static final int INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION = -102;

	/**
	 * Installation return code<br/>
	 * if the parser did not find any certificates in the .apk.
	 */
	public static final int INSTALL_PARSE_FAILED_NO_CERTIFICATES = -103;

	/**
	 * Installation return code<br/>
	 * if the parser found inconsistent certificates on the files in the .apk.
	 */
	public static final int INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES = -104;

	/**
	 * Installation return code<br/>
	 * if the parser encountered a CertificateEncodingException in one of the
	 * files in the .apk.
	 */
	public static final int INSTALL_PARSE_FAILED_CERTIFICATE_ENCODING = -105;

	/**
	 * Installation return code<br/>
	 * if the parser encountered a bad or missing package name in the manifest.
	 */
	public static final int INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME = -106;

	/**
	 * Installation return code<br/>
	 * if the parser encountered a bad shared user id name in the manifest.
	 */
	public static final int INSTALL_PARSE_FAILED_BAD_SHARED_USER_ID = -107;

	/**
	 * Installation return code<br/>
	 * if the parser encountered some structural problem in the manifest.
	 */
	public static final int INSTALL_PARSE_FAILED_MANIFEST_MALFORMED = -108;

	/**
	 * Installation return code<br/>
	 * if the parser did not find any actionable tags (instrumentation or
	 * application) in the manifest.
	 */
	public static final int INSTALL_PARSE_FAILED_MANIFEST_EMPTY = -109;

	/**
	 * Installation return code<br/>
	 * if the system failed to install the package because of system issues.
	 */
	public static final int INSTALL_FAILED_INTERNAL_ERROR = -110;
	/**
	 * Installation return code<br/>
	 * other reason
	 */
	public static final int INSTALL_FAILED_OTHER = -1000000;

	/**
	 * Uninstall return code<br/>
	 * uninstall success.
	 */
	public static final int DELETE_SUCCEEDED = 1;

	/**
	 * Uninstall return code<br/>
	 * uninstall fail if the system failed to delete the package for an
	 * unspecified reason.
	 */
	public static final int DELETE_FAILED_INTERNAL_ERROR = -1;

	/**
	 * Uninstall return code<br/>
	 * uninstall fail if the system failed to delete the package because it is
	 * the active DevicePolicy manager.
	 */
	public static final int DELETE_FAILED_DEVICE_POLICY_MANAGER = -2;

	/**
	 * Uninstall return code<br/>
	 * uninstall fail if pcakge name is invalid
	 */
	public static final int DELETE_FAILED_INVALID_PACKAGE = -3;

	/**
	 * Uninstall return code<br/>
	 * uninstall fail if permission denied
	 */
	public static final int DELETE_FAILED_PERMISSION_DENIED = -4;


	//获取一个apk文件的包名,apkFilepath是apk文件的完整路径
	private static String getPackageFromAPK(Context context, String apkFilepath) {
		try {
			PackageManager packageManager = context.getPackageManager();
			PackageInfo info = null;
			info = packageManager.getPackageArchiveInfo(apkFilepath, PackageManager.GET_ACTIVITIES);
			if (info == null) {
				info = packageManager.getPackageArchiveInfo(apkFilepath, PackageManager.GET_SERVICES);
			}
			if (info == null) {
				info = packageManager.getPackageArchiveInfo(apkFilepath, 0);
			}
			if (info == null) {
				return null;
			} else {
				ApplicationInfo appInfo = null;
				appInfo = info.applicationInfo;
				String packageName = appInfo.packageName;
				if (packageName != null && packageName.length() > 0) {
					return packageName;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
