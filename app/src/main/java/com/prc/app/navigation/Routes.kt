package com.prc.app.navigation

/** All app routes in one place. Arguments in braces. */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val GET_STARTED = "get_started"
    const val LOGIN = "login"
    const val SIGN_UP = "signup"
    const val OTP = "otp/{contact}/{isPhone}/{mode}"   // mode: signup | login | reset
    const val FORGOT = "forgot"
    const val FORGOT_VERIFIED = "forgot_verified/{contact}"   // OTP-cleared contact
    const val HOME = "tabs"
    const val TABS = "tabs/{tab}"                               // tabs with a specific tab selected
    const val JOB = "job/{id}"
    const val POST_JOB = "post_job"
    const val EDIT_JOB = "post_job/{id}"                       // wizard in edit mode
    const val MY_JOBS = "my_jobs"
    const val CHECKOUT_BOOST = "checkout_boost/{id}/{plan}"   // plan: boost_3d | boost_7d
    const val PRO_PLANS = "pro_plans/{src}"   // src: attribution (profile|daily_cap|alert_cap|registration)
    const val PLAN_CHOICE = "plan_choice"
    const val PAYMENTS_HISTORY = "payments_history"
    const val APPLICANTS = "applicants/{id}"
    const val APPLICANT_DETAIL = "applicant/{jobId}/{contact}"   // one applicant's full application
    const val APPLY = "apply/{id}"                               // apply flow for one job
    const val COMPANY = "company/{name}"                         // jobs posted by one company
    const val PROFILE_SETUP = "profile_setup"
    const val PROFILE_EDIT = "profile_edit"
    const val NOTIFICATIONS = "notifications"

    // ==== admin portal ====
    const val ADMIN_LOGIN = "admin_login"
    const val ADMIN_MAIN = "admin_main"

    fun job(id: Int) = "job/$id"
    fun applicants(id: Int) = "applicants/$id"
    fun tabs(tab: String) = "tabs/$tab"

    fun editJob(id: Int) = "post_job/$id"

    fun applicantDetail(jobId: Int, contact: String) =
        "applicant/$jobId/${android.net.Uri.encode(contact)}"

    fun apply(jobId: Int) = "apply/$jobId"

    fun checkoutBoost(jobId: Int, plan: String) = "checkout_boost/$jobId/$plan"

    fun proPlans(source: String) = "pro_plans/$source"

    fun company(name: String) = "company/${android.net.Uri.encode(name)}"

    fun forgotVerified(contact: String) =
        "forgot_verified/${android.net.Uri.encode(contact)}"

    fun otp(contact: String, isPhone: Boolean, mode: String) =
        "otp/${android.net.Uri.encode(contact)}/$isPhone/$mode"
}
