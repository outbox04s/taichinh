package vn.personalfinance.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateInstallResult {
    data object PermissionRequired : UpdateInstallResult
    data object InstallerOpened : UpdateInstallResult
}

@Singleton
class AppUpdateInstaller @Inject constructor(@ApplicationContext private val context:Context) {
    suspend fun install(apkUrl:String,expectedVersion:Long):UpdateInstallResult {
        if(!context.packageManager.canRequestPackageInstalls()){
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return UpdateInstallResult.PermissionRequired
        }
        return withContext(Dispatchers.IO){
            val uri=URI(apkUrl)
            require(uri.scheme=="https"&&uri.host.equals("github.com",true)){"Liên kết cập nhật không hợp lệ"}
            val directory=File(context.cacheDir,"updates").apply{mkdirs()}
            val apk=File(directory,"update-$expectedVersion.apk")
            download(apkUrl,apk)
            verify(apk,expectedVersion)
            openInstaller(apk,expectedVersion)
            UpdateInstallResult.InstallerOpened
        }
    }

    private fun download(apkUrl:String,target:File){
        val connection=(URL(apkUrl).openConnection() as HttpURLConnection).apply{instanceFollowRedirects=true;connectTimeout=20_000;readTimeout=60_000;setRequestProperty("Accept","application/vnd.android.package-archive")}
        try{
            require(connection.responseCode in 200..299){"Không thể tải bản cập nhật (${connection.responseCode})"}
            connection.inputStream.use{input->target.outputStream().use{output->input.copyTo(output)}}
            require(target.length()>100_000){"Tệp cập nhật không hợp lệ"}
        }finally{connection.disconnect()}
    }

    private fun verify(apk:File,expectedVersion:Long){
        @Suppress("DEPRECATION")
        val archive=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
            context.packageManager.getPackageArchiveInfo(apk.path,PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        }else context.packageManager.getPackageArchiveInfo(apk.path,PackageManager.GET_SIGNATURES)
        requireNotNull(archive){"Không đọc được APK cập nhật"}
        require(archive.packageName==context.packageName){"APK không thuộc ứng dụng này"}
        require(PackageInfoCompat.getLongVersionCode(archive)==expectedVersion){"Mã phiên bản APK không khớp"}
        @Suppress("DEPRECATION")
        val installed=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
            context.packageManager.getPackageInfo(context.packageName,PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        }else context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNATURES)
        @Suppress("DEPRECATION")
        val expected=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P) requireNotNull(installed.signingInfo).apkContentsSigners.map{sha256(it.toByteArray())}.toSet() else installed.signatures.orEmpty().map{sha256(it.toByteArray())}.toSet()
        @Suppress("DEPRECATION")
        val actual=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P) requireNotNull(archive.signingInfo).apkContentsSigners.map{sha256(it.toByteArray())}.toSet() else archive.signatures.orEmpty().map{sha256(it.toByteArray())}.toSet()
        require(expected==actual){"Chữ ký APK cập nhật không hợp lệ"}
    }

    private fun sha256(value:ByteArray)=MessageDigest.getInstance("SHA-256").digest(value).joinToString(""){"%02x".format(it)}

    private fun openInstaller(apk:File,version:Long){
        val installer=context.packageManager.packageInstaller
        val params=PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply{
            setAppPackageName(context.packageName)
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S)setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val sessionId=installer.createSession(params)
        installer.openSession(sessionId).use{session->
            session.openWrite("app.apk",0,apk.length()).use{output->apk.inputStream().use{it.copyTo(output)};session.fsync(output)}
            val intent=Intent(context,UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION).putExtra("version",version)
            val pending=PendingIntent.getBroadcast(context,sessionId,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }
}
