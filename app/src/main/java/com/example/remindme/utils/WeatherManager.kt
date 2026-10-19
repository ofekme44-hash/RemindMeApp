package com.example.remindme.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

data class HourlyItem(
    val timeStr: String,
    val icon: String,
    val temp: Int
)

data class WeatherInfo(
    val locationName: String,
    val temperature: Int,
    val conditionText: String,
    val weatherIcon: String,
    val feelsLike: Int,
    val hourlyForecast: List<HourlyItem>,
    val weeklyForecast: List<Triple<String, String, String>>,
    val currentTime: String
)

object WeatherManager {

    suspend fun fetchRealWeather(context: Context, lang: String): WeatherInfo = withContext(Dispatchers.IO) {
        try {
            val ipResponse = URL("https://ipwho.is/").readText()
            val ipJson = JSONObject(ipResponse)

            val city = ipJson.optString("city", "Netanya")
            val country = ipJson.optString("country", "Israel")
            val latitude = ipJson.optDouble("latitude", 32.0853)
            val longitude = ipJson.optDouble("longitude", 34.7818)

            val timezoneObj = ipJson.optJSONObject("timezone")
            val timezoneId = timezoneObj?.optString("id", "Asia/Jerusalem") ?: "Asia/Jerusalem"

            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone(timezoneId)
            }
            val exactLocalTime = timeFormat.format(Date())

            val displayName = when (lang) {
                "he" -> if (country == "Israel") "ישראל • $city" else "$country • $city"
                "ru" -> if (country == "Israel") "Израиль • $city" else "$country • $city"
                "fr" -> if (country == "Israel") "Israël • $city" else "$country • $city"
                else -> "$country • $city"
            }

            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,apparent_temperature,weather_code&hourly=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto"
            val weatherResponse = URL(weatherUrl).readText()
            val weatherJson = JSONObject(weatherResponse)

            val current = weatherJson.getJSONObject("current")
            val currentTemp = current.getDouble("temperature_2m").toInt()
            val feelsLikeTemp = current.getDouble("apparent_temperature").toInt()
            val weatherCode = current.getInt("weather_code")

            val (conditionText, icon) = getWeatherDescriptionAndIcon(weatherCode, lang)

            val hourly = weatherJson.getJSONObject("hourly")
            val timeArray = hourly.getJSONArray("time")
            val tempArray = hourly.getJSONArray("temperature_2m")
            val codeArray = hourly.getJSONArray("weather_code")

            val hourlyList = mutableListOf<HourlyItem>()
            val nowIndex = maxOf(0, Calendar.getInstance(TimeZone.getTimeZone(timezoneId)).get(Calendar.HOUR_OF_DAY))

            for (i in nowIndex until minOf(nowIndex + 6, timeArray.length())) {
                val timeStrFull = timeArray.getString(i)
                val hourOnly = try { timeStrFull.split("T")[1].substring(0, 5) } catch (_: Exception) { "$i:00" }
                val temp = tempArray.getDouble(i).toInt()
                val code = codeArray.getInt(i)
                val (_, hIcon) = getWeatherDescriptionAndIcon(code, lang)

                val displayTime = if (i == nowIndex) {
                    when (lang) { "he" -> "עכשיו"; "ru" -> "Сейчас"; "fr" -> "Maintenant"; else -> "Now" }
                } else {
                    hourOnly
                }

                hourlyList.add(HourlyItem(displayTime, hIcon, temp))
            }

            val daily = weatherJson.getJSONObject("daily")
            val dailyTimes = daily.getJSONArray("time")
            val maxTemps = daily.getJSONArray("temperature_2m_max")
            val minTemps = daily.getJSONArray("temperature_2m_min")
            val dailyCodes = daily.getJSONArray("weather_code")

            val weeklyList = mutableListOf<Triple<String, String, String>>()
            for (i in 0 until minOf(7, dailyTimes.length())) {
                val dateStr = dailyTimes.getString(i)
                val maxT = maxTemps.getDouble(i).toInt()
                val minT = minTemps.getDouble(i).toInt()
                val code = dailyCodes.getInt(i)
                val (_, dayIcon) = getWeatherDescriptionAndIcon(code, lang)

                val formattedDate = formatDate(dateStr, lang)
                weeklyList.add(Triple(formattedDate, dayIcon, "$maxT°C / $minT°C"))
            }

            WeatherInfo(
                locationName = displayName,
                temperature = currentTemp,
                conditionText = conditionText,
                weatherIcon = icon,
                feelsLike = feelsLikeTemp,
                hourlyForecast = hourlyList,
                weeklyForecast = weeklyList,
                currentTime = exactLocalTime
            )
        } catch (e: Exception) {
            e.printStackTrace()
            WeatherInfo(
                locationName = "Israel • Netanya",
                temperature = 26,
                conditionText = "Sunny",
                weatherIcon = "☀️",
                feelsLike = 28,
                hourlyForecast = listOf(HourlyItem("Now", "☀️", 26)),
                weeklyForecast = listOf(Triple("Today", "☀️", "28°C / 23°C")),
                currentTime = "16:38"
            )
        }
    }

    private fun formatDate(dateStr: String, lang: String): String {
        return try {
            val parts = dateStr.split("-")
            if (parts.size == 3) {
                val year = parts[0].toInt()
                val month = parts[1].toInt() - 1
                val day = parts[2].toInt()

                val calendar = Calendar.getInstance().apply {
                    set(year, month, day)
                }
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

                when (lang) {
                    "he" -> {
                        val hebrewDay = when (dayOfWeek) {
                            Calendar.SUNDAY -> "ראשון"
                            Calendar.MONDAY -> "שני"
                            Calendar.TUESDAY -> "שלישי"
                            Calendar.WEDNESDAY -> "רביעי"
                            Calendar.THURSDAY -> "חמישי"
                            Calendar.FRIDAY -> "שישי"
                            Calendar.SATURDAY -> "שבת"
                            else -> ""
                        }
                        val hebrewMonth = when (month) {
                            0 -> "ינו'"
                            1 -> "פבר'"
                            2 -> "מרץ"
                            3 -> "אפר'"
                            4 -> "מאי"
                            5 -> "יוני"
                            6 -> "יולי"
                            7 -> "אוג'"
                            8 -> "ספט'"
                            9 -> "אוק'"
                            10 -> "נוב'"
                            11 -> "דצמ'"
                            else -> ""
                        }
                        "יום $hebrewDay, $day $hebrewMonth"
                    }
                    else -> "$day/$month/$year"
                }
            }
            dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun getWeatherDescriptionAndIcon(code: Int, lang: String): Pair<String, String> {
        return when (code) {
            0 -> Pair(when (lang) { "he" -> "בהיר"; else -> "Sunny" }, "☀️")
            1, 2, 3 -> Pair(when (lang) { "he" -> "מעונן חלקי"; else -> "Partly Cloudy" }, "⛅")
            else -> Pair(when (lang) { "he" -> "נאה"; else -> "Clear" }, "🌤️")
        }
    }
}