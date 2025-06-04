package com.dts.lanprintstar

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.starmicronics.stario10.InterfaceType
import com.starmicronics.stario10.StarConnectionSettings
import com.starmicronics.stario10.StarPrinter
import com.starmicronics.stario10.starxpandcommand.DocumentBuilder
import com.starmicronics.stario10.starxpandcommand.DrawerBuilder
import com.starmicronics.stario10.starxpandcommand.MagnificationParameter
import com.starmicronics.stario10.starxpandcommand.PrinterBuilder
import com.starmicronics.stario10.starxpandcommand.StarXpandCommandBuilder
import com.starmicronics.stario10.starxpandcommand.drawer.OpenParameter
import com.starmicronics.stario10.starxpandcommand.printer.CutType
import com.starmicronics.stario10.starxpandcommand.printer.ImageParameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    var errline = ArrayList<String>()
    var lines = ArrayList<String>()
    var fnames = ArrayList<String>()

    val interfaceType =  InterfaceType.Lan

    var fname=""
    var pname=""
    var mac=""
    var ps=""
    var ordtext=""
    var modo_comanda=false
    var imagePath=""
    var imageflag=false
    var bitmapflag=false
    var drawer=false
    lateinit var imageBitmap: Bitmap


    var comcant=0
    var compos=0

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            if (processBundle()) {
                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed( {  grantPermissions() }, 200)
            } else {
                msgclose("Falta parámetro de impresión")
            }
        } catch (e:Exception) {
            msgclose(object : Any() {}.javaClass.enclosingMethod.name+".(1) "+e.message)
        }
    }

    //region Events


    //endregion

    //region Main

    fun startApplication() {
        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    grandAllFilesAccess()
                    exitApp();return
                }
            }

            if (modo_comanda) {
                impresionComanda()
            } else {
                impresionTexto()
            }


        } catch (e: Exception) {
            errline.add(object : Any() {}.javaClass.enclosingMethod.name+" .(0) "+e.message+"\n")
            //msgclose(object : Any() {}.javaClass.enclosingMethod.name+" .(0) "+e.message)
        }
    }

    fun impresionComanda() {
        errline.clear()
        fnames.clear()

        try {
            var path = Environment.getExternalStorageDirectory().toString()
            val directory: File = File(path)
            val files = directory.listFiles()

            for (itm in files!!) {
                fname = itm.name
                if (fname.indexOf("comanda") == 0) fnames.add(fname)
            }

            comcant=fnames.size
            if (comcant==0) {
                toastlong("No hay pendiente impresion")
                exitApp();return
            }

            compos=0
            procesaImpresionComanda()

        } catch (e: Exception) {
            msgclose(object : Any() {}.javaClass.enclosingMethod.name+" .(2) "+e.message)
            return
        }
    }

    fun impresionTexto() {
        try {
           if (cargaTexto()) {
               imprimeTexto()
           } else {
               msgclose("No se pudo leer archivo de impresión")
           }
        } catch (e: Exception) {
            msgclose(object : Any() {}.javaClass.enclosingMethod.name+" .(3) "+e.message)
        }
    }

    //endregion

    //region Comanda

    private fun procesaImpresionComanda() {
        try {
            if (compos<comcant) {
                fname=fnames.get(compos)
                compos++
                if (cargaComanda()) imprimeComanda()
            } else {
                exitApp()
            }
        } catch (e: Exception) {
            errline.add(object : Any() {}.javaClass.enclosingMethod.name+" .(1) "+e.message+"\n")
        }
    }

    fun cargaComanda() : Boolean {
        try {
            val file = File(Environment.getExternalStorageDirectory().toString() + "/"+fname)
            lines =  file.readLines() as ArrayList<String>
            var ln=0

            ps="";ordtext=""
            for (itm in lines) {
                if (ln==2) {
                    mac = itm
                } else if (ln==3) {
                    ordtext=itm
                } else if (ln>3) {
                    /*
                    if (itm.indexOf("ORDEN #")>=0) {
                        ordtext=itm
                    } else {
                        ps+=itm+"\n"
                    }
                    */
                    ps+=itm+"\n"
                }
                ln++
            }

            return true
        } catch (e: Exception) {
            errline.add(object : Any() {}.javaClass.enclosingMethod.name+" .(2) "+e.message)
            return false
        }
    }

    fun imprimeComanda() {
        var jobcancel=false

        try {
            val settings = StarConnectionSettings(interfaceType, mac)
            val printer = StarPrinter(settings, applicationContext)
            val job = SupervisorJob()
            val scope = CoroutineScope(Dispatchers.Default + job)

            scope.launch {
                try {
                    val builder = StarXpandCommandBuilder()

                    var pbuilder=PrinterBuilder()

                    if (ordtext.isNotEmpty()) {
                        pbuilder.styleMagnification(MagnificationParameter(3,3))
                        pbuilder.actionPrintText(ordtext+"\n")
                        pbuilder.styleMagnification(MagnificationParameter(1,1))
                    }
                    pbuilder.actionPrintText(ps)
                    pbuilder.actionFeedLine(1)
                    pbuilder.actionCut(CutType.Partial)

                    builder.addDocument(DocumentBuilder().addPrinter(pbuilder))

                    val commands = builder.getCommands()

                    try {
                        printer.openAsync().await()
                        printer.printAsync(commands).await()
                    } catch (e: Exception) {
                        jobcancel=true
                        job.cancel()
                    }

                    try {
                        val file: File = File(fname)
                        //file.delete()
                    } catch (e: java.lang.Exception) {
                        errline.add("No se logro borrar archivo de impresion. La impresion se va a repetir.")
                    }

                    printer.closeAsync().await()

                    val handler = Handler(Looper.getMainLooper())
                    handler.postDelayed( { procesaImpresionComanda() }, 500)

                } catch (e: Exception) {
                    try {
                        printer.closeAsync().await()
                    } catch (e: Exception) {}

                    if (jobcancel) {
                        msgclose("La impresora no está conectada: "+mac)
                    } else {
                        errline.add(object : Any() {}.javaClass.enclosingMethod.name+" .(4) "+e.message+"\n")
                    }
                }
            }
        } catch (e: Exception) {
            errline.add(object : Any() {}.javaClass.enclosingMethod.name+" .(5) "+e.message+"\n")
        }
    }

    //endregion

    // region Texto

    fun cargaTexto() : Boolean {
        try {
            val file = File(Environment.getExternalStorageDirectory().toString() + "/print.txt")
            lines =  file.readLines() as ArrayList<String>
            var ln=0

            ps="";ordtext="";imageflag=false

            for (itm in lines) {
                if (ln==2) {
                    mac = itm
                } else if (ln>2) {
                     if (itm.indexOf("@@pic")>=0) {
                         try {
                             imagePath = itm.substring(6)
                             if (imagePath.length>4) imageflag=true
                         } catch (e: Exception) {
                             imageflag=false
                         }
                    } else {
                        ps+=itm+"\n"
                    }
                }
                ln++
            }

            bitmapflag=false
            if (imageflag) {
                try {
                    imageBitmap = loadImage(imagePath)
                    bitmapflag = true
                } catch (imageLoadException: Exception) {
                    toastlong( "No se encontró imagen:" + imagePath)
                }
            }

            return true
        } catch (e: Exception) {
            errline.add(object : Any() {}.javaClass.enclosingMethod.name+" .(6) "+e.message)
            return false
        }
    }

    fun imprimeTexto() {
        var jobcancel=false

        try {
            val settings = StarConnectionSettings(interfaceType, mac)
            val printer = StarPrinter(settings, applicationContext)
            val job = SupervisorJob()
            val scope = CoroutineScope(Dispatchers.Default + job)

            val logoBitmap = BitmapFactory.decodeResource(resources, R.drawable.logompos)

            scope.launch {
                try {
                    val builder = StarXpandCommandBuilder()

                    var pbuilder=PrinterBuilder()
                    pbuilder.actionPrintText(ps)

                    if (bitmapflag) pbuilder.actionPrintImage(ImageParameter(imageBitmap, 250))
                    if (drawer)     pbuilder.actionPrintImage(ImageParameter(logoBitmap, 120))

                    pbuilder.actionFeedLine(1)
                    pbuilder.actionCut(CutType.Partial)

                    val documentbuilder =DocumentBuilder()
                    documentbuilder.addPrinter(pbuilder)
                    if (drawer) documentbuilder.addDrawer(DrawerBuilder().actionOpen(OpenParameter()))

                    builder.addDocument(documentbuilder)

                    val commands = builder.getCommands()

                    try {
                        printer.openAsync().await()
                        printer.printAsync(commands).await()
                    } catch (e: Exception) {
                        jobcancel=true
                        job.cancel()
                    }

                    try {
                        val file: File = File(fname)
                        //file.delete()
                    } catch (e: java.lang.Exception) {
                        errline.add("No se logro borrar archivo de impresion. La impresion se va a repetir.")
                    }

                    printer.closeAsync().await()

                    val handler = Handler(Looper.getMainLooper())
                    handler.postDelayed( { procesaImpresionComanda() }, 500)

                } catch (e: Exception) {
                    try {
                        printer.closeAsync().await()
                    } catch (e: Exception) {}

                    if (jobcancel) {
                        msgclose("La impresora no está conectada: "+mac)
                    } else {
                        errline.add(object : Any() {}.javaClass.enclosingMethod.name+" .(8) "+e.message+"\n")
                    }
                }
            }
        } catch (e: Exception) {
            errline.add(object : Any() {}.javaClass.enclosingMethod.name+" .(9) "+e.message+"\n")
        }
    }

    //endregion

    //region Permission

    private fun grantPermissions() {
        try {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                startApplication()
            } else {
                ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),1)
            }
        } catch (e: java.lang.Exception) {
            toastlong(object : Any() {}.javaClass.enclosingMethod.name + " .(gp) " + e.message)
        }
    }

    override fun onRequestPermissionsResult( requestCode: Int, permissions: Array<out String>, grantResults: IntArray ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        try {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                startApplication()
            } else {
                super.finish()
            }
        } catch (e: java.lang.Exception) {
            toastlong(object : Any() {}.javaClass.enclosingMethod.name + " . " + e.message)
        }
    }

    fun grandAllFilesAccess() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:${applicationContext.packageName}")
            startActivity(intent)
        } catch (ex: java.lang.Exception) {
            msgclose("(4). "+ex.message!!)
        }
    }

    //endregion

    //region Dialogs

    fun msgclose(msg: String) {

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(
            {
                try {
                    val dialog = AlertDialog.Builder(this)
                    dialog.setTitle("Impresion USB")
                    dialog.setMessage(msg)
                    dialog.setCancelable(false)
                    dialog.setNeutralButton("OK") { dialog, which ->
                        val handler = Handler(Looper.getMainLooper())
                        handler.postDelayed( { exitApp() }, 300)
                    }
                    dialog.show()
                } catch (ex: java.lang.Exception) {
                    //toast(ex?.message!!)
                }
            }, 100)

    }

    fun toast(msg: String) {

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(
            {
                try {
                    val toast = Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT)
                    toast.setGravity(Gravity.CENTER, 0, 0)
                    toast.show()
                } catch (ex: java.lang.Exception) {  }
            }, 50)

    }

    fun toastlong(msg: String) {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(
            {
                try {
                    val toast = Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG)
                    toast.setGravity(Gravity.CENTER, 0, 0)
                    toast.show()
                } catch (ex: java.lang.Exception) { }
            }, 100)

    }

    //endregion

    //region Aux

    private fun processBundle() : Boolean {
        val bundle : Bundle
        var bp = ""

        modo_comanda = false

        try {
            bundle = intent.extras!!
            bp = bundle.getString("modo")!!
        } catch (e: java.lang.Exception) {
            return false
        }

        try {
            drawer=false
            var drw = bundle.getString("drawer")!!
            if (drw=="open") drawer=true
        } catch (e: java.lang.Exception) {
            drawer=false
        }

        try {
            if (bp=="c") {
                modo_comanda = true; return true
            } else if (bp=="p") {
                modo_comanda = false; return true
            } else {
               return false
            }
        } catch (e: java.lang.Exception) {
            return false
        }

    }

    private fun loadImage(fname: String): Bitmap {
        val filePath = "${Environment.getExternalStorageDirectory()}/$fname"
        val bitmap = BitmapFactory.decodeFile(filePath)

        if (bitmap == null) {
            throw IllegalArgumentException("No se pudo cargar la imagen: $filePath")
        }

        return bitmap
    }

    fun isValidMacAddress(mac: String): Boolean {
        val macRegex = Regex("^([0-9A-Fa-f]{2}[:]){5}([0-9A-Fa-f]{2})$")
        return macRegex.matches(mac)
    }

    fun exitApp() {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed( {
            exitProcess(0)
        }, 300)
    }

    //endregion

    //region Activity Events

    //endregion

}