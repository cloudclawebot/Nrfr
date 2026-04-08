package com.github.nrfr.manager

import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.android.internal.telephony.ICarrierConfigLoader
import com.github.nrfr.model.SimCardInfo
import rikka.shizuku.ShizukuBinderWrapper

object CarrierConfigManager {
    private const val PACKAGE_NAME = "com.github.nrfr"
    private const val ANDROID_16_SHELL_ERROR = "overrideConfig cannot be invoked by shell"

    fun getSimCards(context: Context): List<SimCardInfo> {
        val simCards = mutableListOf<SimCardInfo>()
        val subId1 = SubscriptionManager.getSubId(0)
        val subId2 = SubscriptionManager.getSubId(1)

        if (subId1 != null) {
            val config1 = getCurrentConfig(subId1[0])
            simCards.add(SimCardInfo(1, subId1[0], getCarrierNameBySubId(context, subId1[0]), config1))
        }
        if (subId2 != null) {
            val config2 = getCurrentConfig(subId2[0])
            simCards.add(SimCardInfo(2, subId2[0], getCarrierNameBySubId(context, subId2[0]), config2))
        }

        return simCards
    }

    private fun getCurrentConfig(subId: Int): Map<String, String> {
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

    private fun getCarrierNameBySubId(context: Context, subId: Int): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return ""

        return try {
            val createForSubscriptionId = TelephonyManager::class.java.getMethod(
                "createForSubscriptionId",
                Int::class.javaPrimitiveType
            )
            val subTelephonyManager = createForSubscriptionId.invoke(telephonyManager, subId) as TelephonyManager
            subTelephonyManager.networkOperatorName
        } catch (_: Throwable) {
            telephonyManager.networkOperatorName
        }
    }

    fun setCarrierConfig(subId: Int, countryCode: String?, carrierName: String? = null) {
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
        overrideCarrierConfig(subId, null)
    }

    private fun overrideCarrierConfig(subId: Int, bundle: PersistableBundle?) {
        try {
            getCarrierConfigLoader().overrideConfig(subId, bundle, true)
        } catch (e: Throwable) {
            val message = e.message.orEmpty()
            if (message.contains(ANDROID_16_SHELL_ERROR, ignoreCase = true)) {
                throw IllegalStateException("当前系统已限制此接口（Android 16/新安全补丁），暂时无法直接保存")
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
