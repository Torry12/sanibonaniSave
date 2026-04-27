package com.sanibonani.save.data.errors

/**
 * Centralized error message mapping.
 * Converts technical exceptions to user-friendly messages.
 * Never exposes sensitive details to users.
 */

object ErrorMessageMapper {
    
    fun mapThrowableToUserMessage(throwable: Throwable?): String {
        if (throwable == null) return "An unknown error occurred"
        
        val message = throwable.message ?: ""
        
        return when {
            // Network errors
            message.contains("Connection refused", ignoreCase = true) ->
                "Unable to connect to servers. Please check your internet connection."
            message.contains("timeout", ignoreCase = true) ->
                "Request took too long. Please check your internet and try again."
            message.contains("Network unreachable", ignoreCase = true) ->
                "No internet connection. Please check your network settings."
            message.contains("DNS", ignoreCase = true) ->
                "Unable to reach servers. Please check your internet connection."
            
            // Authentication errors
            message.contains("invalid login credentials", ignoreCase = true) ->
                "Incorrect email or password. Please try again."
            message.contains("already registered", ignoreCase = true) ->
                "This email is already registered. Please use a different email or sign in."
            message.contains("Invalid email", ignoreCase = true) ->
                "Please enter a valid email address."
            message.contains("Password", ignoreCase = true) ->
                "Password must be at least 6 characters."
            message.contains("Unauthorized", ignoreCase = true) ->
                "You are not authorized to perform this action."
            message.contains("session", ignoreCase = true) ->
                "Your session has expired. Please sign in again."
            
            // Database errors
            message.contains("constraint", ignoreCase = true) ->
                "This record already exists or conflicts with existing data."
            message.contains("unique", ignoreCase = true) ->
                "This value is already in use. Please choose a different one."
            message.contains("foreign key", ignoreCase = true) ->
                "Cannot perform this action because of related records."
            message.contains("not found", ignoreCase = true) ->
                "The requested record was not found."
            
            // Validation errors
            message.contains("invalid", ignoreCase = true) ->
                "Please check your input and try again."
            message.contains("required", ignoreCase = true) ->
                "All required fields must be filled."
            message.contains("length", ignoreCase = true) ->
                "The input is too long or too short."
            
            // Payment errors
            message.contains("payment", ignoreCase = true) ->
                "Payment failed. Please try again or use a different payment method."
            message.contains("card", ignoreCase = true) ->
                "Your card was declined. Please try a different card or contact your bank."
            message.contains("transaction", ignoreCase = true) ->
                "Transaction failed. Please verify your details and try again."
            
            // Server errors
            message.contains("500", ignoreCase = true) ->
                "Server error. Please try again later."
            message.contains("503", ignoreCase = true) ->
                "Service unavailable. Please try again in a few minutes."
            message.contains("404", ignoreCase = true) ->
                "Resource not found."
            message.contains("403", ignoreCase = true) ->
                "You don't have permission to access this."
            
            // Default fallback
            message.isNotBlank() -> "Error: ${message.take(100)}"  // Limit length
            else -> "An unexpected error occurred. Please try again."
        }
    }
    
    fun getErrorTitleForContext(context: String, throwable: Throwable?): String {
        return when (context) {
            "auth" -> "Sign In Failed"
            "signup" -> "Registration Failed"
            "group_create" -> "Group Creation Failed"
            "payment" -> "Payment Failed"
            "group_join" -> "Failed to Join Group"
            "network" -> "Connection Error"
            else -> "Error"
        }
    }
    
    fun isNetworkError(throwable: Throwable?): Boolean {
        val message = throwable?.message ?: return false
        return message.contains("connection", ignoreCase = true) ||
               message.contains("timeout", ignoreCase = true) ||
               message.contains("network", ignoreCase = true) ||
               message.contains("dns", ignoreCase = true)
    }
    
    fun isAuthError(throwable: Throwable?): Boolean {
        val message = throwable?.message ?: return false
        return message.contains("unauthorized", ignoreCase = true) ||
               message.contains("session", ignoreCase = true) ||
               message.contains("auth", ignoreCase = true)
    }
    
    fun isRetryable(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        val message = throwable.message ?: return false
        
        return isNetworkError(throwable) ||
               message.contains("timeout", ignoreCase = true) ||
               message.contains("temporary", ignoreCase = true) ||
               message.contains("unavailable", ignoreCase = true)
    }
}

