package tr.olgun.ortak

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.widget.*
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
 private val dark = Color.rgb(38,48,60)
 private val gray = Color.rgb(93,102,112)
 private val red = Color.rgb(187,32,45)
 private val handler = Handler(Looper.getMainLooper())
 private lateinit var status:TextView
 private lateinit var devices:LinearLayout
 private lateinit var scroll:ScrollView
 private var touchStart=0f
 private var refreshing=false
 private var generation=0
 private var count=0
 private fun dp(x:Int)=(x*resources.displayMetrics.density).toInt()
 private fun text(value:String,size:Float=14f,bold:Boolean=false,color:Int=dark)=TextView(this).apply {
  text=value;textSize=size;setTextColor(color)
  setPadding(dp(5),dp(7),dp(5),dp(7))
  if(bold)setTypeface(null,Typeface.BOLD)
 }
 private fun column()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
 private fun card()=column().apply {
  setPadding(dp(12),dp(9),dp(12),dp(9))
  background=GradientDrawable().apply{setColor(Color.WHITE);cornerRadius=dp(12).toFloat();setStroke(dp(1),Color.rgb(224,230,236))}
 }
 private fun add(parent:LinearLayout,child:android.view.View,top:Int=0){parent.addView(child,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(top)})}
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.statusBarColor=Color.WHITE
  window.navigationBarColor=Color.WHITE
  window.decorView.systemUiVisibility=android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
  val frame=column().apply{setBackgroundColor(Color.rgb(248,250,252))}
  add(frame,text("Ortak",26f,true).apply{setPadding(dp(20),dp(17),dp(20),dp(15));setBackgroundColor(Color.WHITE)})
  scroll=ScrollView(this).apply{isFillViewport=true}
  val body=column().apply{setPadding(dp(15),dp(12),dp(15),dp(12))}
  scroll.addView(body)
  frame.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  status=text("Ağ kontrol ediliyor…",14f,false,gray)
  add(body,status)
  add(body,text("Aynı Ağdaki Cihazlar",20f,true),12)
  add(body,text("Listeyi aşağı çekerek yenileyin. Yalnızca erişilebilen cihazlar görüntülenebilir.",12f,false,gray))
  devices=column();add(body,devices,8)
  add(frame,text("Muhammed Yusuf OLGUN",11f,false,Color.rgb(90,97,106)).apply{gravity=Gravity.CENTER;setPadding(0,dp(8),0,dp(12))})
  setContentView(frame)
  scroll.setOnTouchListener { _,event ->
   when(event.actionMasked){
    MotionEvent.ACTION_DOWN -> touchStart=event.y
    MotionEvent.ACTION_UP -> if(scroll.scrollY==0 && event.y-touchStart>dp(85))refresh()
   }
   false
  }
  refresh()
 }
 private fun connectedNetwork():String? {
  val cm=getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  val net=cm.activeNetwork ?: return null
  val caps=cm.getNetworkCapabilities(net) ?: return null
  if(!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return null
  val lp=cm.getLinkProperties(net) ?: return null
  val ip=lp.linkAddresses.mapNotNull{it.address as? Inet4Address}.firstOrNull{!it.isLoopbackAddress} ?: return null
  return ip.hostAddress
 }
 private fun guess(name:String):String {
  val s=name.lowercase()
  return when {
   listOf("iphone","android","phone","pixel","galaxy","redmi","huawei").any{s.contains(it)} -> "Cep Telefonu"
   listOf("ipad","tablet","tab-").any{s.contains(it)} -> "Tablet"
   listOf("macbook","laptop","notebook").any{s.contains(it)} -> "Laptop"
   listOf("desktop","pc-","imac","workstation").any{s.contains(it)} -> "Desktop"
   listOf("tv","bravia","chromecast","roku").any{s.contains(it)} -> "TV"
   else -> "Diğer"
  }
 }
 private fun probe(ip:String):Boolean {
  val address=InetAddress.getByName(ip)
  if(address.isReachable(230))return true
  for(port in intArrayOf(80,443,445,22,8080)){
   try { Socket().use{it.connect(InetSocketAddress(ip,port),130)};return true }catch(_:Exception){}
  }
  return false
 }
 private fun refresh(){
  if(refreshing)return
  val ip=connectedNetwork()
  devices.removeAllViews();count=0
  if(ip==null){status.text="Wi-Fi ağına bağlanın. Mobil veri üzerinden tarama yapılmaz.";return}
  val prefix=ip.substringBeforeLast('.',"")
  if(prefix.isEmpty()){status.text="IPv4 adresi okunamadı";return}
  refreshing=true
  val token=++generation
  status.text="Bağlı ağ: $ip · Yerel cihazlar taranıyor…"
  Thread {
   val pool=Executors.newFixedThreadPool(24)
   val tasks=(1..254).filter{ "$prefix.$it"!=ip }.map{n ->
    pool.submit {
     val target="$prefix.$n"
     try {
      if(probe(target)){
       val host=try{InetAddress.getByName(target).canonicalHostName}catch(_:Exception){target}
       val name=if(host==target)"Adı belirlenemedi" else host
       handler.post{
        if(token==generation && !isFinishing){
         count++
         val item=card()
         add(item,text(name,17f,true))
         add(item,text("Cihaz türü: "+guess(name),13f,false,red))
         add(item,text("IP adresi: $target",13f))
         add(item,text("Kullanıcı adı: Bilinmiyor",13f,false,gray))
         add(item,text("MAC adresi: Alınamadı",13f,false,gray))
         add(devices,item,8)
         status.text="Bağlı ağ: $ip · Bulunan cihaz: $count"
        }
       }
      }
     }catch(_:Exception){}
    }
   }
   tasks.forEach{try{it.get(18,TimeUnit.SECONDS)}catch(_:Exception){}}
   pool.shutdownNow()
   handler.post{if(token==generation && !isFinishing){refreshing=false;status.text="Bağlı ağ: $ip · Bulunan cihaz: $count · Tarama tamamlandı";if(count==0)add(devices,text("Yanıt veren cihaz bulunamadı. Bazı cihazlar ağ taramasına yanıt vermez.",13f,false,gray))}}
  }.start()
 }
 override fun onDestroy(){generation++;handler.removeCallbacksAndMessages(null);super.onDestroy()}
}