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
    val weeklyForecast: List<Triple<String, String, String>>
)

object WeatherManager {

    suspend fun fetchRealWeather(context: Context, lang: String): WeatherInfo = withContext(Dispatchers.IO) {
        try {
            val ipResponse = URL("https://ipapi.co/json/").readText()
            val ipJson = JSONObject(ipResponse)

            val city = ipJson.optString("city", "Netanya")
            val country = ipJson.optString("country_name", "Israel")
            val latitude = ipJson.optDouble("latitude", 32.0853)
            val longitude = ipJson.optDouble("longitude", 34.7818)

            val displayName = when (lang) {
                "he" -> if (country == "Israel") "ישראל • $city" else "$country • $city"
                "ru" -> if (country == "Israel") "Израиль • $city" else "$country • $city"
                "fr" -> if (country == "Israel") "Israël • $city" else "$country • $city"
                else -> "$country • $city"
            }

            // פנייה ל-Open-Meteo כולל נתונים שעתיים ויומיים
            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,apparent_temperature,weather_code&hourly=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto"
            val weatherResponse = URL(weatherUrl).readText()
            val weatherJson = JSONObject(weatherResponse)

            val current = weatherJson.getJSONObject("current")
            val currentTemp = current.getDouble("temperature_2m").toInt()
            val feelsLikeTemp = current.getDouble("apparent_temperature").toInt()
            val weatherCode = current.getInt("weather_code")

            val (conditionText, icon) = getWeatherDescriptionAndIcon(weatherCode, lang)

            // שליפת תחזית שעתית
            val hourly = weatherJson.getJSONObject("hourly")
            val timeArray = hourly.getJSONArray("time")
            val tempArray = hourly.getJSONArray("temperature_2m")
            val codeArray = hourly.getJSONArray("weather_code")

            val hourlyList = mutableListOf<HourlyItem>()
            val nowIndex = maxOf(0, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

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

            // תחזית יומית עם תמיכה בשפות עבור התאריכים
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

                // המרת התאריך לשפה הנבחרת
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
                weeklyForecast = weeklyList
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
                weeklyForecast = listOf(Triple("Today", "☀️", "28°C / 23°C"))
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
                    "ru" -> {
                        val ruDay = when (dayOfWeek) {
                            Calendar.SUNDAY -> "Вс"
                            Calendar.MONDAY -> "Пн"
                            Calendar.TUESDAY -> "Вт"
                            Calendar.WEDNESDAY -> "Ср"
                            Calendar.THURSDAY -> "Чт"
                            Calendar.FRIDAY -> "Пт"
                            Calendar.SATURDAY -> "Сб"
                            else -> ""
                        }
                        val ruMonth = when (month) {
                            0 -> "янв."
                            1 -> "февр."
                            2 -> "марта"
                            3 -> "апр."
                            4 -> "мая"
                            5 -> "июня"
                            6 -> "июля"
                            7 -> "авг."
                            8 -> "сент."
                            9 -> "окт."
                            10 -> "нояб."
                            11 -> "дек."
                            else -> ""
                        }
                        "$ruDay, $day $ruMonth"
                    }
                    "fr" -> {
                        val frDay = when (dayOfWeek) {
                            Calendar.SUNDAY -> "Dim"
                            Calendar.MONDAY -> "Lun"
                            Calendar.TUESDAY -> "Mar"
                            Calendar.WEDNESDAY -> "Mer"
                            Calendar.THURSDAY -> "Jeu"
                            Calendar.FRIDAY -> "Ven"
                            Calendar.SATURDAY -> "Sam"
                            else -> ""
                        }
                        val frMonth = when (month) {
                            0 -> "janv."
                            1 -> "févr."
                            2 -> "mars"
                            3 -> "avr."
                            4 -> "mai"
                            5 -> "juin"
                            6 -> "juil."
                            7 -> "août"
                            8 -> "sept."
                            9 -> "oct."
                            10 -> "nov."
                            11 -> "déc."
                            else -> ""
                        }
                        "$frDay, $day $frMonth"
                    }
                    else -> {
                        val enDay = when (dayOfWeek) {
                            Calendar.SUNDAY -> "Sun"
                            Calendar.MONDAY -> "Mon"
                            Calendar.TUESDAY -> "Tue"
                            Calendar.WEDNESDAY -> "Wed"
                            Calendar.THURSDAY -> "Thu"
                            Calendar.FRIDAY -> "Fri"
                            Calendar.SATURDAY -> "Sat"
                            else -> ""
                        }
                        val enMonth = when (month) {
                            0 -> "Jan"
                            1 -> "Feb"
                            2 -> "Mar"
                            3 -> "Apr"
                            4 -> "May"
                            5 -> "Jun"
                            6 -> "Jul"
                            7 -> "Aug"
                            8 -> "Sep"
                            9 -> "Oct"
                            10 -> "Nov"
                            11 -> "Dec"
                            else -> ""
                        }
                        "$enDay, $enMonth $day"
                    }
                }
            }
            dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun getWeatherDescriptionAndIcon(code: Int, lang: String): Pair<String, String> {
        return when (code) {
            0 -> Pair(
                when (lang) { "he" -> "בהיר"; "ru" -> "Ясно"; "fr" -> "Ensoleillé"; else -> "Sunny" },
                "☀️"
            )
            1, 2, 3 -> Pair(
                when (lang) { "he" -> "מעונן חלקי"; "ru" -> "Переменная облачность"; "fr" -> "Partiellement nuageux"; else -> "Partly Cloudy" },
                "⛅"
            )
            45, 48 -> Pair(
                when (lang) { "he" -> "ערפל"; "ru" -> "Туман"; "fr" -> "Brouillard"; else -> "Foggy" },
                "🌫️"
            )
            51, 53, 55, 61, 63, 65 -> Pair(
                when (lang) { "he" -> "גשום"; "ru" -> "Дождь"; "fr" -> "Pluvieux"; else -> "Rainy" },
                "🌧️"
            )
            else -> Pair(
                when (lang) { "he" -> "נאה"; "ru" -> "Хорошо"; "fr" -> "Clair"; else -> "Clear" },
                "🌤️"
            )
        }
    }
}