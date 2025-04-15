package com.lu.wxmask.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.ContactsContract
import android.text.TextUtils
import android.util.Log
import com.lu.wxmask.util.entity.CallInfo
import com.lu.wxmask.util.entity.ContactInfo
import com.lu.wxmask.util.entity.SmsInfo
import java.util.Locale
import kotlin.math.min

@Suppress("DEPRECATION")
class PhoneUtils private constructor() {

    companion object {
        const val TAG = "PhoneUtils"

        //获取通话记录列表
        fun getCallInfoList(
            context: Context,
            type: Int, limit: Int, offset: Int, phoneNumber: String?
        ): MutableList<CallInfo> {
            val callInfoList = mutableListOf<CallInfo>()
            try {
                var selection = "1=1"
                val selectionArgs = ArrayList<String>()
                if (type > 0) {
                    selection += " and " + CallLog.Calls.TYPE + " = ?"
                    selectionArgs.add("$type")
                }
                if (!TextUtils.isEmpty(phoneNumber)) {
                    selection += " and " + CallLog.Calls.NUMBER + " like ?"
                    selectionArgs.add("%$phoneNumber%")
                }
                Log.d(TAG, "selection = $selection")
                Log.d(TAG, "selectionArgs = $selectionArgs")

                //为了兼容性这里全部取出后手动分页
                val cursor = context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    null,
                    selection,
                    selectionArgs.toTypedArray(),
                    CallLog.Calls.DEFAULT_SORT_ORDER // + " limit $limit offset $offset"
                ) ?: return callInfoList
                Log.i(TAG, "cursor count:" + cursor.count)

                // 避免超过总数后循环取出
                if (cursor.count == 0 || offset >= cursor.count) {
                    cursor.close()
                    return callInfoList
                }

                if (cursor.moveToFirst()) {
                    Log.d(TAG, "Call ColumnNames=${cursor.columnNames.contentToString()}")
                    val indexName = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val indexNumber = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                    val indexDate = cursor.getColumnIndex(CallLog.Calls.DATE)
                    val indexDuration = cursor.getColumnIndex(CallLog.Calls.DURATION)
                    val indexType = cursor.getColumnIndex(CallLog.Calls.TYPE)
                    val indexViaNumber =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && cursor.getColumnIndex(
                                "via_number"
                            ) != -1
                        ) cursor.getColumnIndex("via_number") else -1
                    var indexSimId =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) cursor.getColumnIndex(
                            CallLog.Calls.PHONE_ACCOUNT_ID
                        ) else -1
                    var indexSubId = indexSimId

                    /**
                     * TODO:卡槽识别，这里需要适配机型
                     * MIUI系统：simid 字段实际为 subscription_id
                     * EMUI系统：subscription_id 实际为 sim_id
                     */
                    var isSimId = false
                    val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault())
                    Log.i(TAG, "manufacturer = $manufacturer")
                    if (manufacturer.contains(Regex(pattern = "xiaomi|redmi"))) {
                        if (cursor.getColumnIndex("simid") != -1) indexSimId =
                            cursor.getColumnIndex("simid")
                        indexSubId = indexSimId
                    } else if (manufacturer.contains(Regex(pattern = "huawei|honor"))) {
                        indexSubId = -1 //TODO:暂时不支持华为
                        isSimId = true
                    }

                    var curOffset = 0
                    do {
                        if (curOffset >= offset) {
                            val callInfo = CallInfo(
                                cursor.getString(indexName) ?: "",  //姓名
                                cursor.getString(indexNumber) ?: "",  //号码
                                cursor.getLong(indexDate),  //获取通话日期
                                cursor.getInt(indexDuration),  //获取通话时长，值为多少秒
                                cursor.getInt(indexType),  //获取通话类型：1.呼入 2.呼出 3.未接
                                if (indexViaNumber != -1) cursor.getString(indexViaNumber) else "",  //来源号码
//                                if (indexSimId != -1) getSimId(cursor.getInt(indexSimId), isSimId) else -1,  //卡槽ID： 0=Sim1, 1=Sim2, -1=获取失败
                                if (indexSubId != -1) cursor.getInt(indexSubId) else 0,  //卡槽主键
                            )
                            Log.d(TAG, callInfo.toString())
                            callInfoList.add(callInfo)
                            if (limit == 1) {
                                cursor.close()
                                return callInfoList
                            }
                        }
                        curOffset++
                        if (curOffset >= offset + limit) break
                    } while (cursor.moveToNext())
                    if (!cursor.isClosed) cursor.close()
                }
            } catch (e: java.lang.Exception) {
                Log.e(TAG, "getCallInfoList:", e)
            }

            return callInfoList
        }

        //获取后一条通话记录
        @SuppressLint("Range")
        fun getLastCallInfo(context: Context, callType: Int, phoneNumber: String?): CallInfo? {
            val callInfoList = getCallInfoList(context, callType, 1, 0, phoneNumber)
            if (callInfoList.isNotEmpty()) return callInfoList?.getOrNull(0)
            return null
        }

        //获取联系人列表
        fun getContactInfoList(
            context: Context,
            limit: Int,
            offset: Int,
            phoneNumber: String?,
            name: String?,
            isFuzzy: Boolean = true
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
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    selection,
                    selectionArgs.toTypedArray(),
                    ContactsContract.CommonDataKinds.Phone.SORT_KEY_PRIMARY
                ) ?: return contactInfoList
                Log.i(TAG, "cursor count:" + cursor.count)

                // 避免超过总数后循环取出
                if (cursor.count == 0 || offset >= cursor.count) {
                    cursor.close()
                    return contactInfoList
                }

                if (cursor.moveToFirst()) {
                    val displayNameIndex =
                        cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val mobileNoIndex =
                        cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
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

        //获取联系人姓名
        fun getContactByNumber(context: Context, phoneNumber: String?): MutableList<ContactInfo> {
            val contactInfoList = mutableListOf<ContactInfo>()
            if (TextUtils.isEmpty(phoneNumber)) return contactInfoList

            // 去除国际区号、空格、括号、横线等字符
            val normalizedInputNumber =
                if (phoneNumber!!.startsWith("+") && phoneNumber.length > 4) {
                    phoneNumber.substring(4).replace("[^0-9]".toRegex(), "")
                } else {
                    phoneNumber.replace("[^0-9]".toRegex(), "")
                }

            contactInfoList.addAll(
                getContactInfoList(
                    context,
                    99,
                    0,
                    normalizedInputNumber,
                    null,
                    false
                )
            )
            if (contactInfoList.isEmpty() || contactInfoList.size == 1) {
                return contactInfoList
            }

            // 计算每个联系人的匹配长度和优先级
            val scoredContacts = contactInfoList.map { contact ->
                //去除空格、括号、横线等字符
                val normalizedContactNumber = contact.phoneNumber.replace("[^0-9]".toRegex(), "")
                val matchLength =
                    calculateMatchLength(normalizedInputNumber, normalizedContactNumber)
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
            }
                .sortedWith(compareByDescending<Pair<ContactInfo, Pair<Int, Int>>> { it.second.first } // 按匹配长度降序
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

        //获取通话记录转发内容
//        fun getCallMsg(callInfo: CallInfo): String {
//            val sb = StringBuilder()
//            sb.append(getString(R.string.contact)).append(callInfo.name).append("\n")
//            if (!TextUtils.isEmpty(callInfo.viaNumber)) sb.append(getString(R.string.via_number)).append(callInfo.viaNumber).append("\n")
//            if (callInfo.dateLong > 0L) sb.append(getString(R.string.call_date)).append(
//                DateUtils.millis2String(
//                    callInfo.dateLong, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
//                )
//            ).append("\n")
//            if (callInfo.duration > 0) {
//                if (callInfo.type == 3) {
//                    sb.append(getString(R.string.ring_duration))
//                } else {
//                    sb.append(getString(R.string.call_duration))
//                }
//                sb.append(callInfo.duration).append("s\n")
//            }
//            sb.append(getString(R.string.mandatory_type))
//            //通话类型：1.来电挂机 2.去电挂机 3.未接来电 4.来电提醒 5.来电接通 6.去电拨出
////            when (callInfo.type) {
////                1 -> sb.append(getString(R.string.incoming_call_ended))
////                2 -> sb.append(getString(R.string.outgoing_call_ended))
////                3 -> sb.append(getString(R.string.missed_call))
////                4 -> sb.append(getString(R.string.incoming_call_received))
////                5 -> sb.append(getString(R.string.incoming_call_answered))
////                6 -> sb.append(getString(R.string.outgoing_call_started))
////                else -> sb.append(getString(R.string.unknown_call))
////            }
//            return sb.toString()
//        }

        // 获取用户短信列表
        fun getSmsInfoList(
            context: Context, type: Int, limit: Int, offset: Int, keyword: String
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
                    Uri.parse("content://sms/"),
                    null,
                    selection,
                    selectionArgs.toTypedArray(),
                    "date desc"
                ) ?: return smsInfoList
                if (offset >= cursorTotal.count) {
                    cursorTotal.close()
                    return smsInfoList
                }

                val cursor = context.contentResolver.query(
                    Uri.parse("content://sms/"),
                    null,
                    selection,
                    selectionArgs.toTypedArray(),
                    "date desc limit $limit offset $offset"
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
                        val contacts = getContactByNumber(context, phoneNumber)
                        smsInfo.name =
                            if (contacts.isNotEmpty()) contacts[0].name else "unknown_number"
                        // 联系人号码
                        smsInfo.number = phoneNumber
                        // 短信内容
                        smsInfo.content = cursor.getString(indexBody)
                        // 短信时间
                        smsInfo.date = cursor.getLong(indexDate)
                        // 短信类型: 1=接收, 2=发送
                        smsInfo.type = cursor.getInt(indexType)
                        // 卡槽ID： 0=Sim1, 1=Sim2, -1=获取失败
//                        smsInfo.simId = if (indexSimId != -1) getSimId(cursor.getInt(indexSimId), isSimId) else -1
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


        //判断是否是手机号码(宽松判断)
        private fun isValidPhoneNumber(phoneNumber: String): Boolean {
            val regex = Regex("^\\+?\\d{3,20}$")
            return regex.matches(phoneNumber)
        }

    }

    init {
        throw UnsupportedOperationException("u can't instantiate me...")
    }
}
