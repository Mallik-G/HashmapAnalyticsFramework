package com.hashmap.dataquality.qualitycheck

import com.hashmap.dataquality.MqttPublisher
import com.hashmap.dataquality.data.Msgs.{InboundMsg, TsKvData}
import com.hashmap.dataquality.metadata.{MetadataService, TagMetaData}
import com.hashmapinc.tempus.MqttConnector
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.{Autowired, Value}
import org.springframework.stereotype.Service

@Service
class FrequencyQualityCheck @Autowired()(metadataFetchService: MetadataService,
                                         mqttConnector: MqttConnector,
                                         @Value("${data-quality-frequency.threhold}") missmatchThreshold: Long) extends QualityCheck {

  private val log = LoggerFactory.getLogger(classOf[FrequencyQualityCheck])

  private val FREQUENCY_MISMATCH_ELEMENTS = "frequencyMismatchElements"

  override def check(deviceId: String, payload: InboundMsg): Unit = {
    val tagsWithMissedFrequency = tagsWithFrequencyMismatch(deviceId, payload.tagList.toList)
    if (tagsWithMissedFrequency.nonEmpty) {
      MqttPublisher.publishAsTelemetry(payload.deviceName, tagsWithMissedFrequency, mqttConnector, FREQUENCY_MISMATCH_ELEMENTS)
      MqttPublisher.publishAsEvents(payload.deviceName, tagsWithMissedFrequency, mqttConnector, FREQUENCY_MISMATCH_ELEMENTS)
    }
  }

  private def tagsWithFrequencyMismatch(deviceId: String, tagList: List[TsKvData]): List[String] = {
    val tagsMetadata = metadataFetchService.getMetadataForDevice(deviceId) match {
      case Right(metadata) => metadata
      case Left(errorMsg: String) => log.error(s"""Missing metadata for $deviceId because "$errorMsg". Tag frequency quality check failed"""); return List.empty
    }
    tagsMetadata.filter(metadata => isFrequencyMismatch(metadata, tagList)).map(_.tag)
  }

  private def isFrequencyMismatch(metadata: TagMetaData, tagList: List[TsKvData]): Boolean = {
    val tagPresenceCount = tagList.map(_.tag).count(metadata.tag.equals(_))
    val timestampRange = tagList.map(_.ts)
    val timeWindow = timestampRange.max - timestampRange.min + 1
    val expectedCount = (timeWindow / metadata.avgTagFrequency.toLong).round

    if (metadata.avgTagFrequency.toLong > timeWindow) {
      log.error(s"Device frequency (${metadata.avgTagFrequency}) cannot be higher than the configured timewindow ($timeWindow)")
      return true
    }

    if (tagPresenceCount == expectedCount) {
      false
    } else if (tagPresenceCount <= expectedCount) {
      (((expectedCount.toFloat - tagPresenceCount.toFloat) / expectedCount.toFloat) * 100) > missmatchThreshold
    } else {
      (((tagPresenceCount.toFloat - expectedCount.toFloat) / tagPresenceCount.toFloat) * 100) > missmatchThreshold
    }
  }
}
