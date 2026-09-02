package vn.personalfinance.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

class UpdateInstallReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)==PackageInstaller.STATUS_PENDING_USER_ACTION){
            val confirmation=if(Build.VERSION.SDK_INT>=33)intent.getParcelableExtra(Intent.EXTRA_INTENT,Intent::class.java) else @Suppress("DEPRECATION")(intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent)
            confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
        }
    }
    companion object{const val ACTION="vn.personalfinance.UPDATE_STATUS"}
}
