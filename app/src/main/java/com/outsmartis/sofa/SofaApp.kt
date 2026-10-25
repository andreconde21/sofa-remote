package com.outsmartis.sofa

import android.app.Application
import com.outsmartis.sofa.data.TvHub

class SofaApp : Application() {
    val hub: TvHub by lazy { TvHub(this) }
}
