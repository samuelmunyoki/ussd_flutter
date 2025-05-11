// package com.phan_tech.ussd_advanced

// import android.app.Activity
// import android.content.Context
// import android.content.Intent
// import android.content.pm.PackageManager
// import android.net.Uri
// import android.os.Build
// import android.os.Handler
// import android.os.Looper
// import android.provider.Settings
// import android.telecom.TelecomManager
// import android.telephony.TelephonyManager
// import android.telephony.TelephonyManager.UssdResponseCallback
// import android.view.accessibility.AccessibilityEvent
// import android.view.accessibility.AccessibilityManager
// import androidx.annotation.NonNull
// import androidx.core.app.ActivityCompat
// import androidx.core.content.ContextCompat
// import io.flutter.embedding.engine.plugins.FlutterPlugin
// import io.flutter.embedding.engine.plugins.activity.ActivityAware
// import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
// import io.flutter.plugin.common.BasicMessageChannel
// import io.flutter.plugin.common.MethodCall
// import io.flutter.plugin.common.MethodChannel
// import io.flutter.plugin.common.MethodChannel.MethodCallHandler
// import io.flutter.plugin.common.MethodChannel.Result
// import io.flutter.plugin.common.StringCodec
// import java.util.concurrent.CompletableFuture

// /** UssdAdvancedPlugin */
// class UssdAdvancedPlugin: FlutterPlugin, MethodCallHandler, ActivityAware, BasicMessageChannel.MessageHandler<String?>  {
//   /// The MethodChannel that will the communication between Flutter and native Android
//   ///
//   /// This local reference serves to register the plugin with the Flutter Engine and unregister it
//   /// when the Flutter Engine is detached from the Activity
//   private lateinit var channel : MethodChannel
//   private var context: Context? = null
//   private var activity: Activity? = null
//   private val ussdApi: USSDApi = USSDController
//   private var event: AccessibilityEvent? = null
//   private lateinit var basicMessageChannel: BasicMessageChannel<String>

//   override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
//     channel = MethodChannel(flutterPluginBinding.binaryMessenger, "method.com.phan_tech/ussd_advanced")
//     channel.setMethodCallHandler(this)
//     this.context = flutterPluginBinding.applicationContext
//     basicMessageChannel = BasicMessageChannel(
//       flutterPluginBinding.binaryMessenger,
//       "message.com.phan_tech/ussd_advanced", StringCodec.INSTANCE
//     )
//   }

//   override fun onAttachedToActivity(binding: ActivityPluginBinding) {
//     activity = binding.activity
//   }

//   override fun onDetachedFromActivity() {
//     activity = null
//   }

//   override fun onDetachedFromActivityForConfigChanges() {
//     // Store activity reference for reattachment
//     onDetachedFromActivity()
//   }

//   override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
//     // Properly reattach to the activity
//     onAttachedToActivity(binding)
//   }

//   private fun setListener(){
//     basicMessageChannel.setMessageHandler(this)
//   }

//   override fun onMessage(message: String?, reply: BasicMessageChannel.Reply<String?>) {
//     if(message != null && event != null){
//       USSDController.send2(message, event!!){
//         event = AccessibilityEvent.obtain(it)
//         try {
//           if(it.text.isNotEmpty()) {
//             reply.reply(it.text.first().toString())
//           }else{
//             reply.reply(null)
//           }
//         } catch (e: Exception){ 
//           reply.reply(null)
//         }
//       }
//     } else {
//       reply.reply(null)
//     }
//   }

//   override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
//     var subscriptionId:Int = 1
//     var code:String? = ""

//     if(call.method == "sendUssd" ||call.method == "sendAdvancedUssd" ||call.method == "multisessionUssd"){
//       val subscriptionIdInteger = call.argument<Int>("subscriptionId")
//         ?: throw RequestParamsException(
//           "Incorrect parameter type: `subscriptionId` must be an int"
//         )
//       subscriptionId = subscriptionIdInteger
//       if (subscriptionId < -1 ) {
//         throw RequestParamsException(
//           "Incorrect parameter value: `subscriptionId` must be >= -1"
//         )
//       }
//       code = call.argument<String>("code")
//       if (code == null) {
//         throw RequestParamsException("Incorrect parameter type: `code` must be a String")
//       }
//       if (code.isEmpty()) {
//         throw RequestParamsException(
//           "Incorrect parameter value: `code` must not be an empty string"
//         )
//       }
//     }

//     // Check if context and activity are available
//     if (context == null && activity != null) {
//       context = activity?.applicationContext
//     }

//     if (context == null) {
//       result.error("NO_CONTEXT", "Context is not available", null)
//       return
//     }

//     when (call.method) {
//       "hasPermissions" -> {
//         result.success(hasPermissions())
//       }
//       "requestPermissions" -> {
//         if (activity != null) {
//           requestPermissions()
//           result.success(null)
//         } else {
//           result.error("NO_ACTIVITY", "Activity is not available", null)
//         }
//       }
//       "sendUssd" -> {
//         result.success(defaultUssdService(code!!, subscriptionId))
//       }
//       "sendAdvancedUssd" -> {
//         if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
//           val res = singleSessionUssd(code!!, subscriptionId)
//           if(res != null){
//             res.exceptionally { e: Throwable? ->
//               if (e is RequestExecutionException) {
//                 result.error(
//                   RequestExecutionException.type, e.message, null
//                 )
//               } else {
//                 result.error(RequestExecutionException.type, e?.message, null)
//               }
//               null
//             }.thenAccept(result::success)
//           }else{
//             result.success(null)
//           }
//         }else{
//           result.success(defaultUssdService(code!!, subscriptionId))
//         }
//       }
//       "multisessionUssd" -> {
//         // check permissions
//         if(!hasPermissions()){
//           if (activity != null) {
//             requestPermissions()
//             result.success(null)
//           } else {
//             result.error("NO_ACTIVITY", "Activity is not available", null)
//           }
//         }else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
//           if (activity != null) {
//             multisessionUssd(code!!, subscriptionId, result)
//           } else {
//             result.error("NO_ACTIVITY", "Activity is not available", null)
//           }
//         }else{
//           result.success(defaultUssdService(code!!, subscriptionId))
//         }
//       }
//       "multisessionUssdCancel" ->{
//         multisessionUssdCancel()
//         result.success(null)
//       }
//       else -> {
//         result.notImplemented()
//       }
//     }
//   }

//   private fun hasPermissions() : Boolean{
//     if (context == null) return false
    
//     return !(
//       ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
//       ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
//       !isAccessibilityServiceEnabled(context!!)
//     )
//   }

//   private fun requestPermissions(){
//     if (context == null || activity == null) return
    
//     if(!isAccessibilityServiceEnabled(context!!)) {
//       val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
//       intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//       context!!.startActivity(intent)
//     }

//     if (ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
//       if (!ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.CALL_PHONE)) {
//         ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.CALL_PHONE), 2)
//       }
//     }

//     if (ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
//       if (!ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.READ_PHONE_STATE)) {
//         ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.READ_PHONE_STATE), 2)
//       }
//     }
//   }

//   override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
//     channel.setMethodCallHandler(null)
//     basicMessageChannel.setMessageHandler(null)
//   }

//   private class RequestExecutionException internal constructor(override var message: String) :
//     Exception() {
//     companion object {
//       var type = "ussd_plugin_ussd_execution_failure"
//     }
//   }

//   private class RequestParamsException internal constructor(override var message: String) :
//     Exception() {
//     companion object {
//       var type = "ussd_plugin_incorrect__parameters"
//     }
//   }

//   // for android 8+
//   private fun singleSessionUssd(ussdCode:String, subscriptionId:Int) : CompletableFuture<String>?{
//     if (context == null || activity == null) return null
    
//     // use default sim
//     val _useDefault: Boolean = subscriptionId == -1

//     if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
//       var res:CompletableFuture<String> = CompletableFuture<String>()
//       // check permissions
//       if (ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
//         if (ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.CALL_PHONE)) {
//         } else {
//           ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.CALL_PHONE), 2)
//         }
//       }

//       // get TelephonyManager
//       val tm = context!!.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

//       val simManager: TelephonyManager = tm.createForSubscriptionId(subscriptionId)

//       // callback
//       val callback =
//         object : UssdResponseCallback() {
//           override fun onReceiveUssdResponse(
//             telephonyManager: TelephonyManager, request: String, response: CharSequence
//           ) {
//             res.complete(response.toString())
//           }

//           override fun onReceiveUssdResponseFailed(
//             telephonyManager: TelephonyManager, request: String, failureCode: Int
//           ) {
//             when (failureCode) {
//               TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> {
//                 res.completeExceptionally(RequestExecutionException("USSD_ERROR_SERVICE_UNAVAIL"))
//               }
//               TelephonyManager.USSD_RETURN_FAILURE -> {
//                 res.completeExceptionally(RequestExecutionException("USSD_RETURN_FAILURE"))
//               }
//               else -> {
//                 res.completeExceptionally(RequestExecutionException("unknown error"))
//               }
//             }
//           }
//         }

//       if(_useDefault){
//         tm.sendUssdRequest(
//           ussdCode,
//           callback,
//           Handler(Looper.getMainLooper())
//         )
//       }else{
//         simManager.sendUssdRequest(
//           ussdCode,
//           callback,
//           Handler(Looper.getMainLooper())
//         )
//       }

//       return res
//     }else{
//       // if sdk is less than 26
//       defaultUssdService(ussdCode, subscriptionId)
//       return null
//     }
//   }

//   private fun multisessionUssd(ussdCode:String, subscriptionId:Int, @NonNull result: Result){
//     if (context == null || activity == null) {
//       result.error("NO_CONTEXT", "Context or activity is not available", null)
//       return
//     }
    
//     var slot = subscriptionId
//     if(subscriptionId == -1){
//       slot = 0
//     }

//     ussdApi.callUSSDInvoke(activity!!, ussdCode, slot, object : USSDController.CallbackInvoke {
//       override fun responseInvoke(ev: AccessibilityEvent) {
//         event = AccessibilityEvent.obtain(ev)
//         setListener()

//         try {
//           if(ev.text.isNotEmpty()) {
//             result.success(java.lang.String.join("\n", ev.text))
//           }else{
//             result.success(null)
//           }
//         }catch (e: Exception){
//           result.success(null)
//         }
//       }

//       override fun over(message: String) {
//         try {
//           basicMessageChannel.send(message)
//           result.success(message)
//           basicMessageChannel.setMessageHandler(null)
//         }catch (e: Exception){
//           result.success(null)
//         }
//       }
//     })
//   }

//   private fun multisessionUssdCancel(){
//     if(event != null){
//       ussdApi.cancel2(event!!)
//       basicMessageChannel.setMessageHandler(null)
//     }
//   }

//   private val simSlotName = arrayOf(
//     "extra_asus_dial_use_dualsim",
//     "com.android.phone.extra.slot",
//     "slot",
//     "simslot",
//     "sim_slot",
//     "subscription",
//     "Subscription",
//     "phone",
//     "com.android.phone.DialingMode",
//     "simSlot",
//     "slot_id",
//     "simId",
//     "simnum",
//     "phone_type",
//     "slotId",
//     "slotIdx"
//   )

//   // multiple for all
//   private fun defaultUssdService(ussdCode:String, subscriptionId:Int): String? {
//     if (context == null || activity == null) return null
    
//     if (ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
//       if (ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.CALL_PHONE)) {
//       } else {
//         ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.CALL_PHONE), 2)
//       }
//       return null
//     }
    
//     try {
//       // use default sim
//       val _useDefault: Boolean = subscriptionId == -1

//       val sim:Int = subscriptionId - 1
//       var number:String = ussdCode
//       number = number.replace("#", "%23")
//       if (!number.startsWith("tel:")) {
//         number = String.format("tel:%s", number)
//       }
//       val intent =
//         Intent(if (isTelephonyEnabled()) Intent.ACTION_CALL else Intent.ACTION_VIEW)
//       intent.data = Uri.parse(number)

//       intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

//       if(!_useDefault){
//         intent.putExtra("com.android.phone.force.slot", true)
//         intent.putExtra("Cdma_Supp", true)

//         for (s in simSlotName) intent.putExtra(s, sim)

//         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
//           if (ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
//             if (ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.READ_PHONE_STATE)) {
//             } else {
//               ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.READ_PHONE_STATE), 2)
//             }
//           }
//           val telecomManager = context!!.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

//           val phoneAccountHandleList = telecomManager.callCapablePhoneAccounts
//           if (phoneAccountHandleList != null && phoneAccountHandleList.isNotEmpty())
//             intent.putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE",
//               phoneAccountHandleList[sim]
//             )
//         }
//       }

//       context!!.startActivity(intent)
//       return "success"

//     } catch (e: Exception) {
//       return null
//     }
//   }

//   private fun isAccessibilityServiceEnabled(context: Context): Boolean{
//     var accessibilityEnabled = false
//     val service = "com.phan_tech.ussd_advanced.USSDServiceKT"
//     val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
//     val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityEvent.TYPES_ALL_MASK)
//     for (enabledService in enabledServices) {
//       val id = enabledService.id
//       if (id.contains(service)) {
//         accessibilityEnabled = true
//         break
//       }
//     }
//     return accessibilityEnabled
//   }

//   private fun isTelephonyEnabled(): Boolean {
//     if (context == null) return false
    
//     val tm = context!!.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
//     return tm.phoneType != TelephonyManager.PHONE_TYPE_NONE
//   }
// }
package com.phan_tech.ussd_advanced

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.UssdResponseCallback
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BasicMessageChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.StringCodec
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/** UssdAdvancedPlugin */
class UssdAdvancedPlugin: FlutterPlugin, MethodCallHandler, ActivityAware, BasicMessageChannel.MessageHandler<String?>  {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private lateinit var channel : MethodChannel
  private var context: Context? = null
  private var activity: Activity? = null
  private val ussdApi: USSDApi = USSDController
  private var event: AccessibilityEvent? = null
  private lateinit var basicMessageChannel: BasicMessageChannel<String>

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "method.com.phan_tech/ussd_advanced")
    channel.setMethodCallHandler(this)
    this.context = flutterPluginBinding.applicationContext
    basicMessageChannel = BasicMessageChannel(
      flutterPluginBinding.binaryMessenger,
      "message.com.phan_tech/ussd_advanced", StringCodec.INSTANCE
    )
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
  }

  override fun onDetachedFromActivityForConfigChanges() {
    // Store activity reference for reattachment
    onDetachedFromActivity()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    // Properly reattach to the activity
    onAttachedToActivity(binding)
  }

  private fun setListener(){
    basicMessageChannel.setMessageHandler(this)
  }

  override fun onMessage(message: String?, reply: BasicMessageChannel.Reply<String?>) {
    if(message != null && event != null){
      USSDController.send2(message, event!!){
        event = AccessibilityEvent.obtain(it)
        try {
          if(it.text.isNotEmpty()) {
            reply.reply(it.text.first().toString())
          }else{
            reply.reply(null)
          }
        } catch (e: Exception){ 
          reply.reply(null)
        }
      }
    } else {
      reply.reply(null)
    }
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    var subscriptionId:Int = 1
    var code:String? = ""

    if(call.method == "sendUssd" ||call.method == "sendAdvancedUssd" ||call.method == "multisessionUssd"){
      val subscriptionIdInteger = call.argument<Int>("subscriptionId")
        ?: throw RequestParamsException(
          "Incorrect parameter type: `subscriptionId` must be an int"
        )
      subscriptionId = subscriptionIdInteger
      if (subscriptionId < -1 ) {
        throw RequestParamsException(
          "Incorrect parameter value: `subscriptionId` must be >= -1"
        )
      }
      code = call.argument<String>("code")
      if (code == null) {
        throw RequestParamsException("Incorrect parameter type: `code` must be a String")
      }
      if (code.isEmpty()) {
        throw RequestParamsException(
          "Incorrect parameter value: `code` must not be an empty string"
        )
      }
    }

    // Check if context and activity are available
    if (context == null && activity != null) {
      context = activity?.applicationContext
    }

    if (context == null) {
      result.error("NO_CONTEXT", "Context is not available", null)
      return
    }

    when (call.method) {
      "hasPermissions" -> {
        result.success(hasPermissions())
      }
      "requestPermissions" -> {
        if (activity != null) {
          requestPermissions()
          result.success(null)
        } else {
          result.error("NO_ACTIVITY", "Activity is not available", null)
        }
      }
      "sendUssd" -> {
        result.success(defaultUssdService(code!!, subscriptionId))
      }
      "sendAdvancedUssd" -> {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
          val res = singleSessionUssd(code!!, subscriptionId)
          if(res != null){
            res.exceptionally { e: Throwable? ->
              if (e is RequestExecutionException) {
                result.error(
                  RequestExecutionException.type, e.message, null
                )
              } else {
                result.error(RequestExecutionException.type, e?.message, null)
              }
              null
            }.thenAccept(result::success)
          }else{
            result.success(null)
          }
        }else{
          result.success(defaultUssdService(code!!, subscriptionId))
        }
      }
      "multisessionUssd" -> {
        // check permissions
        if(!hasPermissions()){
          if (activity != null) {
            requestPermissions()
            result.success(null)
          } else {
            result.error("NO_ACTIVITY", "Activity is not available", null)
          }
        }else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
          if (activity != null) {
            multisessionUssd(code!!, subscriptionId, result)
          } else {
            result.error("NO_ACTIVITY", "Activity is not available", null)
          }
        }else{
          result.success(defaultUssdService(code!!, subscriptionId))
        }
      }
      "multisessionUssdCancel" ->{
        multisessionUssdCancel()
        result.success(null)
      }
      else -> {
        result.notImplemented()
      }
    }
  }

  private fun hasPermissions() : Boolean{
    if (context == null) return false
    
    return !(
      ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
      !isAccessibilityServiceEnabled(context!!)
    )
  }

  private fun requestPermissions(){
    if (context == null || activity == null) return
    
    if(!isAccessibilityServiceEnabled(context!!)) {
      val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
      intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
      context!!.startActivity(intent)
    }

    if (ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
      if (!ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.CALL_PHONE)) {
        ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.CALL_PHONE), 2)
      }
    }

    if (ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
      if (!ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.READ_PHONE_STATE)) {
        ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.READ_PHONE_STATE), 2)
      }
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)
    basicMessageChannel.setMessageHandler(null)
  }

  private class RequestExecutionException internal constructor(override var message: String) :
    Exception() {
    companion object {
      var type = "ussd_plugin_ussd_execution_failure"
    }
  }

  private class RequestParamsException internal constructor(override var message: String) :
    Exception() {
    companion object {
      var type = "ussd_plugin_incorrect__parameters"
    }
  }

  // for android 8+
  private fun singleSessionUssd(ussdCode:String, subscriptionId:Int) : CompletableFuture<String>?{
    if (context == null || activity == null) return null
    
    // use default sim
    val _useDefault: Boolean = subscriptionId == -1

    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
      var res:CompletableFuture<String> = CompletableFuture<String>()
      // check permissions
      if (ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.CALL_PHONE)) {
        } else {
          ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.CALL_PHONE), 2)
        }
      }

      // get TelephonyManager
      val tm = context!!.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

      val simManager: TelephonyManager = tm.createForSubscriptionId(subscriptionId)

      // callback
      val callback =
        object : UssdResponseCallback() {
          override fun onReceiveUssdResponse(
            telephonyManager: TelephonyManager, request: String, response: CharSequence
          ) {
            res.complete(response.toString())
          }

          override fun onReceiveUssdResponseFailed(
            telephonyManager: TelephonyManager, request: String, failureCode: Int
          ) {
            when (failureCode) {
              TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> {
                res.completeExceptionally(RequestExecutionException("USSD_ERROR_SERVICE_UNAVAIL"))
              }
              TelephonyManager.USSD_RETURN_FAILURE -> {
                res.completeExceptionally(RequestExecutionException("USSD_RETURN_FAILURE"))
              }
              else -> {
                res.completeExceptionally(RequestExecutionException("unknown error"))
              }
            }
          }
        }

      if(_useDefault){
        tm.sendUssdRequest(
          ussdCode,
          callback,
          Handler(Looper.getMainLooper())
        )
      }else{
        simManager.sendUssdRequest(
          ussdCode,
          callback,
          Handler(Looper.getMainLooper())
        )
      }

      return res
    }else{
      // if sdk is less than 26
      defaultUssdService(ussdCode, subscriptionId)
      return null
    }
  }

  private fun multisessionUssd(ussdCode:String, subscriptionId:Int, @NonNull result: Result){
    if (context == null || activity == null) {
      result.error("NO_CONTEXT", "Context or activity is not available", null)
      return
    }
    
    var slot = subscriptionId
    if(subscriptionId == -1){
      slot = 0
    }

    // Add this flag to track if result has been sent
    val resultSent = AtomicBoolean(false)

    ussdApi.callUSSDInvoke(activity!!, ussdCode, slot, object : USSDController.CallbackInvoke {
      override fun responseInvoke(ev: AccessibilityEvent) {
        event = AccessibilityEvent.obtain(ev)
        setListener()

        try {
          if(ev.text.isNotEmpty() && !resultSent.getAndSet(true)) {
            result.success(java.lang.String.join("\n", ev.text))
          } else if (!resultSent.get()) {
            resultSent.set(true)
            result.success(null)
          }
        } catch (e: Exception){
          if (!resultSent.getAndSet(true)) {
            result.success(null)
          }
        }
      }

      override fun over(message: String) {
        try {
          basicMessageChannel.send(message)
          // Only send result if it hasn't been sent yet
          if (!resultSent.getAndSet(true)) {
            result.success(message)
          }
          basicMessageChannel.setMessageHandler(null)
        } catch (e: Exception){
          if (!resultSent.getAndSet(true)) {
            result.success(null)
          }
        }
      }
    })
  }

  private fun multisessionUssdCancel(){
    if(event != null){
      ussdApi.cancel2(event!!)
      basicMessageChannel.setMessageHandler(null)
    }
  }

  private val simSlotName = arrayOf(
    "extra_asus_dial_use_dualsim",
    "com.android.phone.extra.slot",
    "slot",
    "simslot",
    "sim_slot",
    "subscription",
    "Subscription",
    "phone",
    "com.android.phone.DialingMode",
    "simSlot",
    "slot_id",
    "simId",
    "simnum",
    "phone_type",
    "slotId",
    "slotIdx"
  )

  // multiple for all
  private fun defaultUssdService(ussdCode:String, subscriptionId:Int): String? {
    if (context == null || activity == null) return null
    
    if (ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
      if (ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.CALL_PHONE)) {
      } else {
        ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.CALL_PHONE), 2)
      }
      return null
    }
    
    try {
      // use default sim
      val _useDefault: Boolean = subscriptionId == -1

      val sim:Int = subscriptionId - 1
      var number:String = ussdCode
      number = number.replace("#", "%23")
      if (!number.startsWith("tel:")) {
        number = String.format("tel:%s", number)
      }
      val intent =
        Intent(if (isTelephonyEnabled()) Intent.ACTION_CALL else Intent.ACTION_VIEW)
      intent.data = Uri.parse(number)

      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

      if(!_useDefault){
        intent.putExtra("com.android.phone.force.slot", true)
        intent.putExtra("Cdma_Supp", true)

        for (s in simSlotName) intent.putExtra(s, sim)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
          if (ContextCompat.checkSelfPermission(context!!, android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity!!, android.Manifest.permission.READ_PHONE_STATE)) {
            } else {
              ActivityCompat.requestPermissions(activity!!, arrayOf(android.Manifest.permission.READ_PHONE_STATE), 2)
            }
          }
          val telecomManager = context!!.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

          val phoneAccountHandleList = telecomManager.callCapablePhoneAccounts
          if (phoneAccountHandleList != null && phoneAccountHandleList.isNotEmpty())
            intent.putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE",
              phoneAccountHandleList[sim]
            )
        }
      }

      context!!.startActivity(intent)
      return "success"

    } catch (e: Exception) {
      return null
    }
  }

  private fun isAccessibilityServiceEnabled(context: Context): Boolean{
    var accessibilityEnabled = false
    val service = "com.phan_tech.ussd_advanced.USSDServiceKT"
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityEvent.TYPES_ALL_MASK)
    for (enabledService in enabledServices) {
      val id = enabledService.id
      if (id.contains(service)) {
        accessibilityEnabled = true
        break
      }
    }
    return accessibilityEnabled
  }

  private fun isTelephonyEnabled(): Boolean {
    if (context == null) return false
    
    val tm = context!!.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    return tm.phoneType != TelephonyManager.PHONE_TYPE_NONE
  }
}