package com.minimalist.launcher.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseProvider {

    private const val SUPABASE_URL = "https://yxtwogepsysjzwcfocuu.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_publishable_1yI6FLQfQ1lfkBCO6R-uwg_BYavDyj2"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
        }
    }
}
