package com.norbula.mingxue.config.security

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream
import javax.annotation.PostConstruct

@Configuration
class AuthenticationInitializer {
    @Value("\${firebase.service.account.key}")
    private lateinit var serviceAccountKeyPath: String

    @PostConstruct
    fun initialize() {
        val serviceAccount = FileInputStream(serviceAccountKeyPath)
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .build()
        FirebaseApp.initializeApp(options)

    }
}