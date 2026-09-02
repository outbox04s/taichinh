@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package vn.personalfinance.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val VietnameseDate = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))

@Composable
fun DatePickerField(label:String,value:String,allowClear:Boolean=false,modifier:Modifier=Modifier,onChange:(String)->Unit){
    var showPicker by remember{mutableStateOf(false)}
    val selected=remember(value){runCatching{LocalDate.parse(value)}.getOrNull()}
    OutlinedButton(onClick={showPicker=true},modifier=modifier.fillMaxWidth().heightIn(min=64.dp),contentPadding=PaddingValues(horizontal=16.dp,vertical=8.dp)){
        Icon(Icons.Rounded.CalendarMonth,contentDescription=null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f),horizontalAlignment=Alignment.Start){
            Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(selected?.format(VietnameseDate)?:"Chọn ngày",fontWeight=FontWeight.Medium)
        }
        if(allowClear&&selected!=null)TextButton(onClick={onChange("")}){Text("Xóa")}
    }
    if(showPicker){
        val initialMillis=selected?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val pickerState=rememberDatePickerState(initialSelectedDateMillis=initialMillis)
        DatePickerDialog(onDismissRequest={showPicker=false},confirmButton={
            TextButton(onClick={pickerState.selectedDateMillis?.let{millis->onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())};showPicker=false}){Text("Chọn")}
        },dismissButton={TextButton(onClick={showPicker=false}){Text("Hủy")}}){
            DatePicker(state=pickerState,title={Text("Chọn $label",Modifier.padding(start=24.dp,top=16.dp))},showModeToggle=true)
        }
    }
}
