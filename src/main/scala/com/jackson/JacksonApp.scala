package com.jackson

import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar

import com.jackson.http.DownloadHelper.{download, httpCall}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.io.Source

object JacksonApp {
  def logger = LoggerFactory.getLogger(this.getClass)

  val conf = ConfigFactory.load();
  //val parseConf = ConfigFactory.parseFile(new File("application.properties"))
  //val conf = ConfigFactory.load(parseConf)

  val url1 = conf.getString("url.studio.classroom")
  val url2 = conf.getString("url.hls-vod")
  val today = new SimpleDateFormat("yyyyMMdd").format(Calendar.getInstance().getTime())
  val downloadPath = s"${conf.getString("path.download.root")}/${today}"
  val defaultM3u8 = conf.getString("default.m3u8")
  val resolution = conf.getString("resolution")

  def main(args: Array[String]) {
    //get .m3u8
    val m3u8: Option[String] = defaultM3u8 match {
      case null | "" => httpCall(url1) { c =>
        (c.lines.toSeq.dropWhile { x => !(x contains "m3u8") || (x.startsWith("	//")) } headOption) match {
          case None => ""
          case Some(s) => s.split("\"").filter { x => x contains ".m3u8" }(0)
        }
      }
      case a => Some(defaultM3u8)
    }

    logger.info(s".m3u8: ${m3u8.get}")

    //get .mp4.m3u8
    val mp4m3u8 = httpCall(m3u8.get) { c => (c.split("#") filter { x => x contains s"${resolution}.mp4.m3u8" } head).split("hls-vod/sc") tail }.get(0).replace("\n", "").replace("\r", "")
    logger.info(s".mp4.m3u8: ${url2}${mp4m3u8}")

    //create folder for now day
    new File(downloadPath).mkdirs

    //download .mp4.m3u8
    download(url2 + mp4m3u8, s"${downloadPath}/${mp4m3u8}")

    //download .ts
    val futures: Future[List[Option[String]]] = Future.sequence {

      val tss = Source.fromFile(s"${downloadPath}/${mp4m3u8}").getLines.filterNot { x => x.startsWith("#EXT") }.toList

      logger.info(s"total files:${tss.size}")

      tss.map { ts => {
        val future: Future[Option[String]] = Future {
          download(s"${url2}/${ts}", s"${downloadPath}/${ts}")
        }
        future onSuccess {
          case sb => logger.info(s"${ts} - download success")
        }
        future onFailure {
          case ex: Exception => logger.error(s"${ts} - download failure", ex)
        }
        future
      }
      }
    }
    Await.result(futures, Duration.Inf)
  }
}
 
