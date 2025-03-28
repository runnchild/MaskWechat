# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn com.google.common.collect.ArrayListMultimap
-dontwarn com.google.common.collect.Multimap
-dontwarn java.awt.Color
-dontwarn java.awt.Font
-dontwarn java.awt.Point
-dontwarn java.awt.Rectangle
-dontwarn javax.money.CurrencyUnit
-dontwarn javax.money.Monetary
-dontwarn javax.servlet.ServletOutputStream
-dontwarn javax.servlet.http.HttpServletRequest
-dontwarn javax.servlet.http.HttpServletResponse
-dontwarn javax.ws.rs.Consumes
-dontwarn javax.ws.rs.Produces
-dontwarn javax.ws.rs.RuntimeType
-dontwarn javax.ws.rs.WebApplicationException
-dontwarn javax.ws.rs.core.Configurable
-dontwarn javax.ws.rs.core.Configuration
-dontwarn javax.ws.rs.core.Context
-dontwarn javax.ws.rs.core.Feature
-dontwarn javax.ws.rs.core.FeatureContext
-dontwarn javax.ws.rs.core.MediaType
-dontwarn javax.ws.rs.core.MultivaluedMap
-dontwarn javax.ws.rs.core.Response
-dontwarn javax.ws.rs.core.StreamingOutput
-dontwarn javax.ws.rs.ext.ContextResolver
-dontwarn javax.ws.rs.ext.MessageBodyReader
-dontwarn javax.ws.rs.ext.MessageBodyWriter
-dontwarn javax.ws.rs.ext.Provider
-dontwarn javax.ws.rs.ext.Providers
-dontwarn org.glassfish.jersey.CommonProperties
-dontwarn org.glassfish.jersey.internal.spi.AutoDiscoverable
-dontwarn org.glassfish.jersey.internal.util.PropertiesHelper
-dontwarn org.javamoney.moneta.Money
-dontwarn org.joda.time.DateTime
-dontwarn org.joda.time.DateTimeZone
-dontwarn org.joda.time.Duration
-dontwarn org.joda.time.Instant
-dontwarn org.joda.time.LocalDate
-dontwarn org.joda.time.LocalDateTime
-dontwarn org.joda.time.LocalTime
-dontwarn org.joda.time.Period
-dontwarn org.joda.time.ReadablePartial
-dontwarn org.joda.time.format.DateTimeFormat
-dontwarn org.joda.time.format.DateTimeFormatter
-dontwarn org.springframework.core.MethodParameter
-dontwarn org.springframework.core.ResolvableType
-dontwarn org.springframework.core.annotation.Order
-dontwarn org.springframework.data.redis.serializer.RedisSerializer
-dontwarn org.springframework.data.redis.serializer.SerializationException
-dontwarn org.springframework.http.HttpHeaders
-dontwarn org.springframework.http.HttpInputMessage
-dontwarn org.springframework.http.HttpOutputMessage
-dontwarn org.springframework.http.MediaType
-dontwarn org.springframework.http.converter.AbstractHttpMessageConverter
-dontwarn org.springframework.http.converter.GenericHttpMessageConverter
-dontwarn org.springframework.http.converter.HttpMessageNotReadableException
-dontwarn org.springframework.http.converter.HttpMessageNotWritableException
-dontwarn org.springframework.http.server.ServerHttpRequest
-dontwarn org.springframework.http.server.ServerHttpResponse
-dontwarn org.springframework.http.server.ServletServerHttpRequest
-dontwarn org.springframework.messaging.Message
-dontwarn org.springframework.messaging.MessageHeaders
-dontwarn org.springframework.messaging.converter.AbstractMessageConverter
-dontwarn org.springframework.util.Assert
-dontwarn org.springframework.util.CollectionUtils
-dontwarn org.springframework.util.MimeType
-dontwarn org.springframework.util.ObjectUtils
-dontwarn org.springframework.util.StringUtils
-dontwarn org.springframework.validation.BindingResult
-dontwarn org.springframework.web.bind.annotation.ControllerAdvice
-dontwarn org.springframework.web.bind.annotation.ResponseBody
-dontwarn org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice
-dontwarn org.springframework.web.servlet.view.AbstractView
-dontwarn org.springframework.web.socket.sockjs.frame.AbstractSockJsMessageCodec
-dontwarn retrofit2.Converter$Factory
-dontwarn retrofit2.Converter
-dontwarn retrofit2.Retrofit
-dontwarn springfox.documentation.spring.web.json.Json