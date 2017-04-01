package com.jackson.http

import java.io.{Closeable, File, FileOutputStream}

import org.apache.http.client.methods.{HttpGet, HttpRequestBase}
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.protocol.BasicHttpContext
import org.apache.http.{HttpEntity, HttpStatus}
import org.slf4j.LoggerFactory

import scala.util.Properties

object DownloadHelper {

  val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/57.0.2987.110 Safari/537.36"
  val RETRY_COUNT = 10

  def logger = LoggerFactory.getLogger(this.getClass)

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

  def httpRep[T](httprb: HttpRequestBase, c: HttpEntity => T, retry: Integer = 0) :Option[T] = {
    //setting user agent and header
    val HTTP_CONTEXT = new BasicHttpContext()
    HTTP_CONTEXT.setAttribute("http.useragent", USER_AGENT)

    CallThenClose(HttpClientBuilder.create().build())(http => {
      val httpResponse = http.execute(httprb, HTTP_CONTEXT)
      val status = httpResponse.getStatusLine.getStatusCode

      if (status == HttpStatus.SC_OK) {
        val entity = httpResponse.getEntity
        if (entity != null)
          Some(c(entity))
        else None
      }
      else {
        if (RETRY_COUNT >= retry) {
          val count = retry + 1
          logger.info(s"retry download ${httprb.getURI}......(${count})")
          httpRep(httprb, c, count)
        } else {
          throw new Exception(s"can not retrieve content from ${httprb.getURI}, status code is ${status}")
        }
      }
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
    })
    fileNm
  }

  def getFileName(SaveTo: String): String = {
    val isValid = Option(SaveTo).exists { s => (s.trim.nonEmpty) && (s.contains(Properties.lineSeparator)) }
    if (isValid) {
      SaveTo.split(Properties.lineSeparator).last
    } else SaveTo
  }
}

