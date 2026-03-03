package kz.innlab.template.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import java.io.ByteArrayInputStream
import java.util.Base64

@Configuration
@EnableAsync
class FirebaseConfig {

    @Bean
    @ConditionalOnProperty(name = ["app.firebase.enabled"], havingValue = "true")
    fun firebaseApp(): FirebaseApp {
        if (FirebaseApp.getApps().isEmpty()) {
            val credentialsJson = System.getenv("FIREBASE_CREDENTIALS_JSON")
                ?: throw IllegalStateException("FIREBASE_CREDENTIALS_JSON environment variable is not set")

            val decodedBytes = Base64.getDecoder().decode(credentialsJson)
            val credentials = GoogleCredentials.fromStream(ByteArrayInputStream(decodedBytes))

            val options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build()

            return FirebaseApp.initializeApp(options)
        }
        return FirebaseApp.getInstance()
    }
}
