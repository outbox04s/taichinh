package vn.personalfinance.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import vn.personalfinance.MainActivity
import vn.personalfinance.domain.repository.FinanceRepository
import vn.personalfinance.presentation.toVnd

@HiltWorker class DebtReminderWorker @AssistedInject constructor(@Assisted context:Context,@Assisted params:WorkerParameters,private val repository:FinanceRepository):CoroutineWorker(context,params){
 override suspend fun doWork():Result=repository.claimDebtReminders().fold({items->val manager=applicationContext.getSystemService(NotificationManager::class.java);manager.createNotificationChannel(NotificationChannel("debt_due","Nhắc lịch trả nợ",NotificationManager.IMPORTANCE_HIGH));items.forEach{r->val text=when(r.type){"overdue"->"Đã quá hạn: ";"due"->"Đến hạn hôm nay: ";else->"Sắp đến hạn: " }+r.remainingAmount.toVnd();val intent=PendingIntent.getActivity(applicationContext,r.installmentId.hashCode(),Intent(applicationContext,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);manager.notify((r.installmentId+r.dueDate+r.type).hashCode(),NotificationCompat.Builder(applicationContext,"debt_due").setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(r.debtName).setContentText(text).setContentIntent(intent).setAutoCancel(true).build())};Result.success()},{Result.retry()})
}
