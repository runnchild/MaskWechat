package com.example.libcontacts.entity

import java.io.Serializable

data class TaskSetting(
    val type: Int, // TASK_CONDITION_FRAGMENT_LIST 索引加上 KEY_BACK_CODE_CONDITION 或者 TASK_ACTION_FRAGMENT_LIST 索引加上 KEY_BACK_CODE_ACTION
    val title: String, //标题
    val description: String, //描述
    var setting: String = "", //设置
    var position: Int = -1 //位置
) : Serializable {

}
