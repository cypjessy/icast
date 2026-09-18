package com.prc.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.prc.app.data.AuthRepository
import com.prc.app.ui.screens.jobs.ApplyJobScreen
import com.prc.app.ui.screens.jobs.JobDetailsScreen
import com.prc.app.ui.screens.profile.ProfileSetupScreen
import com.prc.app.ui.screens.notifications.NotificationsScreen
import com.prc.app.ui.screens.post.ApplicantDetailScreen
import com.prc.app.ui.screens.post.ApplicantsScreen
import com.prc.app.ui.screens.post.MyJobsScreen
import com.prc.app.ui.screens.post.PostJobScreen
import com.prc.app.ui.screens.auth.ForgotPasswordScreen
import com.prc.app.ui.screens.auth.GetStartedScreen
import com.prc.app.ui.screens.auth.LoginScreen
import com.prc.app.ui.screens.auth.OtpScreen
import com.prc.app.ui.screens.auth.SignUpScreen
import com.prc.app.ui.screens.tabs.MainTabsScreen
import com.prc.app.ui.screens.onboarding.OnboardingScreen
import com.prc.app.ui.screens.splash.SplashScreen
import com.prc.app.ui.screens.admin.AdminLoginScreen
import com.prc.app.ui.screens.admin.AdminMainScreen

@Composable
fun AppNavGraph(navController: NavHostController) {
    val user by AuthRepository.currentUser.collectAsState()
    val onboardingSeen by AuthRepository.onboardingSeen.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    val destination = when {
                        user != null -> Routes.HOME
                        !onboardingSeen -> Routes.ONBOARDING
                        else -> Routes.GET_STARTED
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = {
                AuthRepository.markOnboardingSeen()
                navController.navigate(Routes.GET_STARTED) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.GET_STARTED) {
            GetStartedScreen(
                onContinuePhone = { navController.navigate(Routes.SIGN_UP) },
                onContinueGoogle = { navController.navigate(Routes.SIGN_UP) },
                onNavigateToLogin = { navController.navigate(Routes.LOGIN) }
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToSignUp = { navController.navigate(Routes.SIGN_UP) },
                onNavigateToForgot = { navController.navigate(Routes.FORGOT) }
            )
        }
        composable(Routes.SIGN_UP) {
            SignUpScreen(
                onSignUpSuccess = {
                    // Firebase account created — go straight to profile setup
                    navController.navigate(Routes.PROFILE_SETUP) {
                        popUpTo(Routes.SIGN_UP) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.OTP,
            arguments = listOf(
                navArgument("contact") { type = NavType.StringType },
                navArgument("isPhone") { type = NavType.BoolType },
                navArgument("mode") { type = NavType.StringType }
            )
        ) { entry ->
            val contact = entry.arguments?.getString("contact").orEmpty()
            val isPhone = entry.arguments?.getBoolean("isPhone") ?: true
            val mode = entry.arguments?.getString("mode") ?: "signup"
            OtpScreen(
                contact = contact,
                isPhone = isPhone,
                mode = mode,
                onVerified = {
                    val dest = when (mode) {
                        "reset" -> Routes.forgotVerified(contact)
                        "signup" -> Routes.PROFILE_SETUP
                        else -> Routes.HOME
                    }
                    navController.navigate(dest) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.FORGOT) {
            ForgotPasswordScreen(
                otpVerifiedContact = null,
                onNavigateToOtp = { c, p ->
                    navController.navigate(Routes.otp(c, p, "reset"))
                },
                onDone = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Routes.FORGOT_VERIFIED,
            arguments = listOf(navArgument("contact") { type = NavType.StringType })
        ) { entry ->
            ForgotPasswordScreen(
                otpVerifiedContact = entry.arguments?.getString("contact") ?: "",
                onNavigateToOtp = { _, _ -> },
                onDone = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            MainTabsScreen(navController = navController)
        }
        composable(
            route = Routes.TABS,
            arguments = listOf(navArgument("tab") { type = NavType.StringType })
        ) { entry ->
            MainTabsScreen(
                navController = navController,
                initialTab = entry.arguments?.getString("tab") ?: "home"
            )
        }
        composable(
            route = Routes.JOB,
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { entry ->
            val jobId = entry.arguments?.getInt("id") ?: -1
            JobDetailsScreen(
                jobId = jobId,
                onBack = { navController.popBackStack() },
                onViewApplicants = { id -> navController.navigate(Routes.applicants(id)) },
                onApply = { id -> navController.navigate(Routes.apply(id)) }
            )
        }
        composable(
            route = Routes.APPLY,
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { entry ->
            val jobId = entry.arguments?.getInt("id") ?: -1
            ApplyJobScreen(
                jobId = jobId,
                onClose = { navController.popBackStack() },
                onSubmitted = { navController.popBackStack() },
                onUpgradeToPro = {
                    com.prc.app.data.PlanAnalytics.recordUpgradeAttempt("daily_cap")
                    navController.navigate(Routes.proPlans("daily_cap"))
                },
                onViewApplications = {
                    navController.navigate(Routes.tabs("applications")) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
                onBackToSearch = {
                    navController.navigate(Routes.tabs("search")) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                }
            )
        }
        composable(
            route = Routes.COMPANY,
            arguments = listOf(navArgument("name") { type = NavType.StringType })
        ) { entry ->
            val companyName = entry.arguments?.getString("name").orEmpty()
            com.prc.app.ui.screens.company.CompanyJobsScreen(
                companyName = companyName,
                onBack = { navController.popBackStack() },
                onJobClick = { id -> navController.navigate(Routes.job(id)) }
            )
        }
        composable(Routes.PROFILE_SETUP) {
            ProfileSetupScreen(
                isEditMode = false,
                onDone = {
                    // First-run flow: present the Free vs Pro pricing choice
                    // once before landing on the home feed.
                    navController.navigate(Routes.PLAN_CHOICE) {
                        popUpTo(Routes.PROFILE_SETUP) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.PLAN_CHOICE) {
            com.prc.app.ui.screens.payments.PlanChoiceScreen(
                onChoseFree = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PLAN_CHOICE) { inclusive = true }
                    }
                },
                onProSubscribed = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PLAN_CHOICE) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.PROFILE_EDIT) {
            ProfileSetupScreen(
                isEditMode = true,
                onDone = { navController.popBackStack() }
            )
        }
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.POST_JOB) {
            PostJobScreen(
                onPosted = {
                    navController.navigate(Routes.MY_JOBS) {
                        popUpTo(Routes.POST_JOB) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.EDIT_JOB,
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { entry ->
            val jobId = entry.arguments?.getInt("id") ?: -1
            PostJobScreen(
                editJobId = jobId,
                onPosted = {
                    navController.navigate(Routes.MY_JOBS) {
                        popUpTo(Routes.POST_JOB) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.MY_JOBS) {
            MyJobsScreen(
                onJobClick = { id -> navController.navigate(Routes.applicants(id)) },
                onPostClick = { navController.navigate(Routes.POST_JOB) },
                onEditJob = { id -> navController.navigate(Routes.editJob(id)) },
                onBoostCheckout = { jobId, plan ->
                    navController.navigate(Routes.checkoutBoost(jobId, plan.id))
                }
            )
        }
        composable(
            route = Routes.CHECKOUT_BOOST,
            arguments = listOf(
                navArgument("id") { type = NavType.IntType },
                navArgument("plan") { type = NavType.StringType }
            )
        ) { entry ->
            val jobId = entry.arguments?.getInt("id") ?: -1
            val planId = entry.arguments?.getString("plan") ?: "boost_3d"
            val plan: com.prc.app.data.BillingProduct =
                if (planId == "boost_7d") com.prc.app.data.BillingProduct.Boost7d()
                else com.prc.app.data.BillingProduct.Boost3d()
            com.prc.app.ui.screens.payments.CheckoutScreen(
                product = plan,
                jobId = jobId,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.APPLICANTS,
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { entry ->
            val jobId = entry.arguments?.getInt("id") ?: -1
            ApplicantsScreen(
                jobId = jobId,
                onBack = { navController.popBackStack() },
                onOpenApplicant = { contact ->
                    navController.navigate(Routes.applicantDetail(jobId, contact))
                }
            )
        }
        // ==== admin portal ====
        composable(Routes.PAYMENTS_HISTORY) {
            com.prc.app.ui.screens.payments.PaymentsHistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.PRO_PLANS,
            arguments = listOf(navArgument("src") { type = NavType.StringType; defaultValue = "profile" })
        ) { entry ->
            com.prc.app.ui.screens.payments.ProPlansScreen(
                entrySource = entry.arguments?.getString("src") ?: "profile",
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ADMIN_LOGIN) {
            AdminLoginScreen(
                onBack = { navController.popBackStack() },
                onLoginSuccess = {
                    navController.navigate(Routes.ADMIN_MAIN) {
                        popUpTo(Routes.ADMIN_LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ADMIN_MAIN) {
            AdminMainScreen(
                onLogout = {
                    com.prc.app.data.AdminRepository.logout()
                    navController.navigate(Routes.tabs("profile")) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Routes.APPLICANT_DETAIL,
            arguments = listOf(
                navArgument("jobId") { type = NavType.IntType },
                navArgument("contact") { type = NavType.StringType }
            )
        ) { entry ->
            val jobId = entry.arguments?.getInt("jobId") ?: -1
            val contact = entry.arguments?.getString("contact").orEmpty()
            ApplicantDetailScreen(
                jobId = jobId,
                applicantContact = contact,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
