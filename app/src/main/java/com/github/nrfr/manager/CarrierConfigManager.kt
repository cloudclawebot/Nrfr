package com.github.nrfr.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.android.internal.telephony.ICarrierConfigLoader
import com.github.nrfr.model.SimCardInfo
import rikka.shizuku.ShizukuBinderWrapper

object CarrierConfigManager {
    private const val PACKAGE_NAME = "com.cloudclawebot.nrfr"
    private const val ANDROID_16_SHELL_ERROR = "overrideConfig cannot be invoked by shell"

    fun getSimCards(context: Context): List<SimCardInfo> {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return emptyList()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val subscriptions = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                subscriptionManager.activeSubscriptionInfoList.orEmpty()
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())

        return subscriptions
            .filter { it.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            .sortedBy { it.simSlotIndex }
            .mapIndexed { index, info ->
                val subId = info.subscriptionId
                val slot = if (info.simSlotIndex >= 0) info.simSlotIndex + 1 else index + 1
                SimCardInfo(
                    slot = slot,
                    subId = subId,
                    carrierName = getCarrierNameBySubInfo(context, info),
                    currentConfig = getCurrentConfig(subId)
                )
            }
    }

    private fun getCurrentConfig(subId: Int): Map<String, String> {
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return emptyMap()
        }

        return try {
            val config = getCarrierConfigLoader().getConfigForSubId(subId, PACKAGE_NAME) ?: return emptyMap()
            val result = mutableMapOf<String, String>()

            config.getString(CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING)?.let {
                result["国家码"] = it
            }

            if (config.getBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, false)) {
                config.getString(CarrierConfigManager.KEY_CARRIER_NAME_STRING)?.let {
                    result["运营商名称"] = it
                }
            }

            result
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun getCarrierNameBySubInfo(context: Context, info: SubscriptionInfo): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return info.carrierName?.toString().orEmpty()

        return try {
            val createForSubscriptionId = TelephonyManager::class.java.getMethod(
                "createForSubscriptionId",
                Int::class.javaPrimitiveType
            )
            val subTelephonyManager = createForSubscriptionId.invoke(telephonyManager, info.subscriptionId) as TelephonyManager
            subTelephonyManager.networkOperatorName.takeUnless { it.isNullOrBlank() }
                ?: info.carrierName?.toString().orEmpty()
        } catch (_: Throwable) {
            info.carrierName?.toString()?.takeUnless { it.isBlank() } ?: telephonyManager.networkOperatorName
        }
    }

    fun setCarrierConfig(subId: Int, countryCode: String?, carrierName: String? = null) {
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            throw IllegalArgumentException("当前设备没有可用的 SIM 卡")
        }

        val bundle = PersistableBundle()

        if (!countryCode.isNullOrEmpty() && countryCode.length == 2) {
            bundle.putString(
                CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING,
                countryCode.lowercase()
            )
        }

        if (!carrierName.isNullOrEmpty()) {
            bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_NAME_OVERRIDE_BOOL, true)
            bundle.putString(CarrierConfigManager.KEY_CARRIER_NAME_STRING, carrierName)
        }

        overrideCarrierConfig(subId, bundle)
    }

    fun resetCarrierConfig(subId: Int) {
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            throw IllegalArgumentException("当前设备没有可用的 SIM 卡")
        }
        overrideCarrierConfig(subId, null)
    }

    private fun overrideCarrierConfig(subId: Int, bundle: PersistableBundle?) {
        val loader = getCarrierConfigLoader()
        try {
            loader.overrideConfig(subId, bundle, true)
            return
        } catch (e: Throwable) {
            val message = e.message.orEmpty()
            if (message.contains(ANDROID_16_SHELL_ERROR, ignoreCase = true)) {
                try {
                    loader.overrideConfig(subId, bundle, false)
                    return
                } catch (fallbackError: Throwable) {
                    val fallbackMessage = fallbackError.message.orEmpty()
                    if (fallbackMessage.contains(ANDROID_16_SHELL_ERROR, ignoreCase = true)) {
                        throw IllegalStateException("当前系统已限制持久化写入；已尝试临时写入但仍失败（Android 16/新安全补丁）")
                    }
                    throw fallbackError
                }
            }
            throw e
        }
    }

    private fun getCarrierConfigLoader(): ICarrierConfigLoader {
        val telephonyFrameworkInitializer = Class.forName("android.telephony.TelephonyFrameworkInitializer")
        val getTelephonyServiceManager = telephonyFrameworkInitializer.getDeclaredMethod("getTelephonyServiceManager")
        val telephonyServiceManager = getTelephonyServiceManager.invoke(null)
        val getRegisterer = telephonyServiceManager.javaClass.getDeclaredMethod("getCarrierConfigServiceRegisterer")
        val registerer = getRegisterer.invoke(telephonyServiceManager)
        val getService = registerer.javaClass.getDeclaredMethod("get")
        val binder = getService.invoke(registerer) as android.os.IBinder

        return ICarrierConfigLoader.Stub.asInterface(ShizukuBinderWrapper(binder))
    }
}
