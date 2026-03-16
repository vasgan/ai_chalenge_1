package com.example.vasganchalenge1.rag.data.loading

import android.content.Context
import com.example.vasganchalenge1.rag.model.PdfExtractionResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfTextExtractor @Inject constructor(
    @ApplicationContext context: Context
) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    fun extract(inputStream: InputStream): PdfExtractionResult {
        PDDocument.load(inputStream).use { document ->
            val text = PDFTextStripper().getText(document)
            return PdfExtractionResult(
                text = text,
                pageCount = document.numberOfPages
            )
        }
    }
}
