package com.jackson.http

import java.io.{Closeable, File, FileOutputStream}

import org.apache.http.client.methods.{HttpGet, HttpRequestBase}
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.{HttpEntity, HttpStatus}

import scala.util.Properties

object DownloadHelper {

  def CallThenClose[A, T <: Closeable](a: T)(c: T => A) = {
    try {
      c(a)
    } finally {
      a.close
    }
  }

  def CallThenClose[A, T <: Closeable, Z <: Closeable](a: T, b: Z)(c: (T, Z) => A) = {
    try {
      c(a, b)
    } finally {
      a.close
      b.close
    }
  }

  def httpRep[T](httprb: HttpRequestBase, c: HttpEntity => T) = {

    CallThenClose(HttpClientBuilder.create().build())(http => {
      val httpResponse = http.execute(httprb)
      val status = httpResponse.getStatusLine.getStatusCode
      if (status != HttpStatus.SC_OK) {
        throw new Exception(s"can not retrieve content from ${httprb.getURI}, status code is ${status}")
      }
      val entity = httpResponse.getEntity
      if (entity != null)
        Some(c(entity))
      else None
    })
  }

  def httpGet[T](http: HttpGet, c: String => T) = httpRep(http, entity => {
    c(CallThenClose(entity.getContent)(is => {
      io.Source.fromInputStream(is).mkString
    }))
  }
  )

  def httpCall[T](url: String)(c: String => T) = {
    httpGet(new HttpGet(url), c)
  }

  def download(downloadFrom: String, SaveTo: String): Option[String] = {
    val fileNm = httpRep(new HttpGet(downloadFrom), (entity) => {
      val is = entity.getContent
      val fos = new FileOutputStream(new File(SaveTo))
      CallThenClose(is, fos)((is, fos) => {
        Iterator
          .continually(is.read)
          .takeWhile(-1 !=)
          .foreach(fos.write)
      })
      getFileName(downloadFrom)
    }
    )
    fileNm
  }

  def getFileName(SaveTo: String): String = {
    val isValid = Option(SaveTo).exists { s => (s.trim.nonEmpty) && (s.contains(Properties.lineSeparator)) }
    if (isValid) {
      SaveTo.split(Properties.lineSeparator).last
    } else SaveTo
  }
}

