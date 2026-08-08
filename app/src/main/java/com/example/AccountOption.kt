package com.example

data class AccountOption(
    val username: String,
    val pumpName: String,
    val role: String,
    val description: String = "",
    val mobileNumber: String,
    val ownerAdminPhone: String
)
