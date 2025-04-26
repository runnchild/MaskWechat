import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.text.TextUtils
import android.util.Log
import com.example.libcontacts.entity.ContactInfo
import com.example.libcontacts.entity.SmsInfo
import java.util.Locale
import kotlin.math.min

@SuppressLint("StaticFieldLeak")
object PhoneUtils {
    lateinit var context: Context

    private val TAG = "PhoneUtils"

    fun getSmsInfoList(
        type: Int, limit: Int, offset: Int, keyword: String
    ): MutableList<SmsInfo> {
        val smsInfoList: MutableList<SmsInfo> = mutableListOf()
        try {
            var selection = "1=1"
            val selectionArgs = ArrayList<String>()
            if (type > 0) {
                selection += " and type = ?"
                selectionArgs.add("$type")
            }
            if (!TextUtils.isEmpty(keyword)) {
                selection += " and body like ?"
                selectionArgs.add("%$keyword%")
            }
            Log.d(TAG, "selection = $selection")
            Log.d(TAG, "selectionArgs = $selectionArgs")

            // 避免超过总数后循环取出
            val cursorTotal = context.contentResolver.query(
                Uri.parse("content://sms/"), null, selection, selectionArgs.toTypedArray(), "date desc"
            ) ?: return smsInfoList
            if (offset >= cursorTotal.count) {
                cursorTotal.close()
                return smsInfoList
            }

            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/"), null, selection, selectionArgs.toTypedArray(), "date desc limit $limit offset $offset"
            ) ?: return smsInfoList

            Log.i(TAG, "cursor count:" + cursor.count)
            if (cursor.count == 0) {
                cursor.close()
                return smsInfoList
            }

            if (cursor.moveToFirst()) {
                Log.d(TAG, "SMS ColumnNames=${cursor.columnNames.contentToString()}")
                val indexAddress = cursor.getColumnIndex("address")
                val indexBody = cursor.getColumnIndex("body")
                val indexDate = cursor.getColumnIndex("date")
                val indexType = cursor.getColumnIndex("type")
                var indexSimId = cursor.getColumnIndex("sim_id")
                var indexSubId = cursor.getColumnIndex("sub_id")

                /**
                 * TODO:卡槽识别，这里需要适配机型
                 * MIUI系统：sim_id 字段实际为 subscription_id
                 * EMUI系统：sub_id 实际为 sim_id
                 */
                var isSimId = false
                val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault())
                Log.i(TAG, "manufacturer = $manufacturer")
                if (manufacturer.contains(Regex(pattern = "xiaomi|redmi"))) {
                    indexSubId = cursor.getColumnIndex("sim_id")
                } else if (manufacturer.contains(Regex(pattern = "huawei|honor"))) {
                    indexSimId = cursor.getColumnIndex("sub_id")
                    isSimId = true
                }

                do {
                    val smsInfo = SmsInfo()
                    val phoneNumber = cursor.getString(indexAddress)
                    // 根据手机号码查询用户名
                    val contacts = getContactByNumber(phoneNumber)
                    smsInfo.name = if (contacts.isNotEmpty()) contacts[0].name else "未知"
                    // 联系人号码
                    smsInfo.number = phoneNumber
                    // 短信内容
                    smsInfo.content = cursor.getString(indexBody)
                    // 短信时间
                    smsInfo.date = cursor.getLong(indexDate)
                    // 短信类型: 1=接收, 2=发送
                    smsInfo.type = cursor.getInt(indexType)
                    // 卡槽ID： 0=Sim1, 1=Sim2, -1=获取失败
//                    smsInfo.simId = if (indexSimId != -1) getSimId(cursor.getInt(indexSimId), isSimId) else -1
                    // 卡槽主键
                    smsInfo.subId = if (indexSubId != -1) cursor.getInt(indexSubId) else 0

                    smsInfoList.add(smsInfo)
                } while (cursor.moveToNext())

                if (!cursorTotal.isClosed) cursorTotal.close()
                if (!cursor.isClosed) cursor.close()
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            Log.e(TAG, "getSmsInfoList:", e)
        }
        return smsInfoList
    }
    //获取联系人姓名
    fun getContactByNumber(phoneNumber: String?): MutableList<ContactInfo> {
        val contactInfoList = mutableListOf<ContactInfo>()
        if (TextUtils.isEmpty(phoneNumber)) return contactInfoList

        // 去除国际区号、空格、括号、横线等字符
        val normalizedInputNumber = if (phoneNumber!!.startsWith("+") && phoneNumber.length > 4) {
            phoneNumber.substring(4).replace("[^0-9]".toRegex(), "")
        } else {
            phoneNumber.replace("[^0-9]".toRegex(), "")
        }

        contactInfoList.addAll(getContactInfoList(99, 0, normalizedInputNumber, null, false))
        if (contactInfoList.isEmpty() || contactInfoList.size == 1) {
            return contactInfoList
        }

        // 计算每个联系人的匹配长度和优先级
        val scoredContacts = contactInfoList.map { contact ->
            //去除空格、括号、横线等字符
            val normalizedContactNumber = contact.phoneNumber.replace("[^0-9]".toRegex(), "")
            val matchLength = calculateMatchLength(normalizedInputNumber, normalizedContactNumber)
            // 优先级规则：
            // 1. 完全匹配（输入手机号与联系人手机号完全一致）：优先级 2
            // 2. 匹配长度等于输入手机号长度：优先级 1
            // 3. 其他情况：优先级 0
            val priority = when {
                normalizedInputNumber == normalizedContactNumber -> 2
                matchLength == normalizedInputNumber.length -> 1
                else -> 0
            }
            contact to Pair(matchLength, priority)
        }.sortedWith(compareByDescending<Pair<ContactInfo, Pair<Int, Int>>> { it.second.first } // 按匹配长度降序
            .thenByDescending { it.second.second }) // 按优先级降序

        // 返回匹配长度最长且优先级最高的联系人列表
        val maxMatchLength = scoredContacts.first().second.first
        val maxPriority = scoredContacts.first().second.second
        return scoredContacts
            .filter { it.second.first == maxMatchLength && it.second.second == maxPriority }
            .map { it.first }
            .toMutableList()
    }

    // 计算从右向左的匹配长度
    private fun calculateMatchLength(number1: String, number2: String): Int {
        var matchLength = 0
        val minLength = min(number1.length, number2.length)

        // 从右向左逐位比较
        for (i in 1..minLength) {
            if (number1[number1.length - i] == number2[number2.length - i]) {
                matchLength++
            } else {
                break // 遇到不匹配的字符，停止比较
            }
        }

        return matchLength
    }

    fun getContactInfoList(
        limit: Int, offset: Int, phoneNumber: String?, name: String?, isFuzzy: Boolean = true
    ): MutableList<ContactInfo> {
        val contactInfoList: MutableList<ContactInfo> = mutableListOf()

        try {
            var selection = "1=1"
            val selectionArgs = ArrayList<String>()
            if (!TextUtils.isEmpty(phoneNumber)) {
                selection += " and replace(replace(" + ContactsContract.CommonDataKinds.Phone.NUMBER + ",' ',''),'-','') like ?"
                if (isFuzzy) {
                    selectionArgs.add("%$phoneNumber%")
                } else {
                    selectionArgs.add("%$phoneNumber")
                }
            }
            if (!TextUtils.isEmpty(name)) {
                selection += " and " + ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " like ?"
                selectionArgs.add("%$name%")
            }
            Log.d(TAG, "selection = $selection")
            Log.d(TAG, "selectionArgs = $selectionArgs")

            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, selection, selectionArgs.toTypedArray(), ContactsContract.CommonDataKinds.Phone.SORT_KEY_PRIMARY
            ) ?: return contactInfoList
            Log.i(TAG, "cursor count:" + cursor.count)

            // 避免超过总数后循环取出
            if (cursor.count == 0 || offset >= cursor.count) {
                cursor.close()
                return contactInfoList
            }

            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val mobileNoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                do {
                    val contactInfo = ContactInfo(
                        cursor.getString(displayNameIndex),  //姓名
                        cursor.getString(mobileNoIndex),  //号码
                    )
                    Log.d(TAG, contactInfo.toString())
                    contactInfoList.add(contactInfo)
                    if (limit == 1) {
                        cursor.close()
                        return contactInfoList
                    }
                } while (cursor.moveToNext())
                if (!cursor.isClosed) cursor.close()
            }
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "getContactInfoList:", e)
        }

        return contactInfoList
    }

    /**
     * 将 subscription_id 转成 卡槽ID： 0=Sim1, 1=Sim2, -1=获取失败
     *
     * TODO: 这里有坑，每个品牌定制系统的字段不太一样，不一定能获取到卡槽ID
     * 测试通过：MIUI   测试失败：原生 Android 11（Google Pixel 2 XL）
     *
     * @param mId SubscriptionId
     * @param isSimId 是否已经是SimId无需转换（待做机型兼容）
     */
//    private fun getSimId(mId: Int, isSimId: Boolean): Int {
//        Log.i(TAG, "mId = $mId, isSimId = $isSimId")
//        if (isSimId) return mId
//
//        if (SettingUtils.subidSim1 > 0 || SettingUtils.subidSim2 > 0) {
//            return if (mId == SettingUtils.subidSim1) 0 else 1
//        } else {
//            //获取卡槽信息
//            if (App.SimInfoList.isEmpty()) {
//                App.SimInfoList = getSimMultiInfo()
//            }
//            Log.i(TAG, "SimInfoList = " + App.SimInfoList.toString())
//
//            val simSlot = -1
//            if (App.SimInfoList.isEmpty()) return simSlot
//            for (simInfo in App.SimInfoList.values) {
//                if (simInfo.mSubscriptionId == mId && simInfo.mSimSlotIndex != -1) {
//                    Log.i(TAG, "simInfo = $simInfo")
//                    return simInfo.mSimSlotIndex
//                }
//            }
//            return simSlot
//        }
//    }

    //判断是否是手机号码(宽松判断)
    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val regex = Regex("^\\+?\\d{3,20}$")
        return regex.matches(phoneNumber)
    }

}