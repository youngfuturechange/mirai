package net.mamoe.mirai.network.packet

import net.mamoe.mirai.message.defaults.MessageChain
import net.mamoe.mirai.message.defaults.PlainText
import net.mamoe.mirai.utils.toUHexString
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.util.zip.GZIPInputStream

/**
 * @author Him188moe
 */
open class ServerEventPacket(input: DataInputStream, val packetId: ByteArray, val eventIdentity: ByteArray) : ServerPacket(input) {

    override fun decode() {

    }
}

/**
 * Android 客户端上线
 */
class ServerAndroidOnlineEventPacket(input: DataInputStream, packetId: ByteArray, eventIdentity: ByteArray) : ServerEventPacket(input, packetId, eventIdentity)

/**
 * Android 客户端上线
 */
class ServerAndroidOfflineEventPacket(input: DataInputStream, packetId: ByteArray, eventIdentity: ByteArray) : ServerEventPacket(input, packetId, eventIdentity)

/**
 * 群文件上传
 */
class ServerGroupUploadFileEventPacket(input: DataInputStream, packetId: ByteArray, eventIdentity: ByteArray) : ServerEventPacket(input, packetId, eventIdentity) {
    lateinit var xmlMessage: String

    override fun decode() {
        xmlMessage = String(this.input.goto(65).readNBytes(this.input.goto(60).readShort().toInt()))
    }//todo test
}

class ServerGroupMessageEventPacket(input: DataInputStream, packetId: ByteArray, eventIdentity: ByteArray) : ServerEventPacket(input, packetId, eventIdentity) {
    var groupNumber: Int = 0
    var qq: Int = 0
    lateinit var message: String
    lateinit var messageType: MessageType

    enum class MessageType {
        NORMAL,
        XML,
        AT,
        FACE,//qq自带表情 [face107.gif]

        PLAIN_TEXT, //纯文本
        IMAGE, //自定义图片 {F50C5235-F958-6DF7-4EFA-397736E125A4}.gif

        ANONYMOUS,//匿名用户发出的消息

        OTHER,
    }

    override fun decode() {
        groupNumber = this.input.goto(51).readInt()
        qq = this.input.goto(56).readInt()
        val fontLength = this.input.goto(108).readShort()
        //println(this.input.goto(110 + fontLength).readNBytesAt(2).toUHexString())//always 00 00

        messageType = when (val id = this.input.goto(110 + fontLength + 2).readByte().toInt()) {
            19 -> MessageType.NORMAL
            14 -> MessageType.XML
            6 -> MessageType.AT


            1 -> MessageType.PLAIN_TEXT
            2 -> MessageType.FACE
            3 -> MessageType.IMAGE
            25 -> MessageType.ANONYMOUS

            else -> {
                println("id=$id")
                MessageType.OTHER
            }
        }


        when (messageType) {
            MessageType.NORMAL -> {
                val gzippedMessage = this.input.goto(110 + fontLength + 16).readNBytes(this.input.goto(110 + fontLength + 3).readShort().toInt() - 11)
                ByteArrayOutputStream().let {
                    GZIPInputStream(gzippedMessage.inputStream()).transferTo(it)
                    message = String(it.toByteArray())
                }
            }

            MessageType.XML -> {
                val gzippedMessage = this.input.goto(110 + fontLength + 9).readNBytes(this.input.goto(110 + fontLength + 3).readShort().toInt() - 4)
                ByteArrayOutputStream().let {
                    GZIPInputStream(gzippedMessage.inputStream()).transferTo(it)
                    message = String(it.toByteArray())
                }
            }

            MessageType.FACE -> {
                val faceId = this.input.goto(110 + fontLength + 8).readByte()
                message = "[face${faceId}.gif]"
            }

            MessageType.AT, MessageType.OTHER, MessageType.PLAIN_TEXT, MessageType.IMAGE, MessageType.ANONYMOUS -> {
                var messageLength: Int = this.input.goto(110 + fontLength + 6).readShort().toInt()
                message = String(this.input.goto(110 + fontLength + 8).readNBytes(messageLength))

                val oeLength: Int
                if (this.input.readByte().toInt() == 6) {
                    oeLength = this.input.readShort().toInt()
                    this.input.skip(4)
                    val messageLength2 = this.input.readShort().toInt()
                    val message2 = String(this.input.readNBytes(messageLength2))
                    message += message2
                    messageLength += messageLength2
                } else {
                    oeLength = this.input.readShort().toInt()
                }

                //读取 nick, ignore.
                /*
                when (this.input.goto(110 + fontLength + 3 + oeLength).readByteAt().toInt()) {
                    12 -> {
                        this.input.skip(4)//maybe 5?

                    }
                    19 -> {

                    }
                    0x0E -> {

                    }
                    else -> {
                    }
                }*/
            }
        }
    }
}

class ServerFriendMessageEventPacket(input: DataInputStream, packetId: ByteArray, eventIdentity: ByteArray) : ServerEventPacket(input, packetId, eventIdentity) {
    var qq: Int = 0
    lateinit var message: MessageChain


    @ExperimentalUnsignedTypes
    override fun decode() {
        //start at Sep1.0:27
        input.goto(0)
        println(input.readAllBytes().toUHexString())
        input.goto(0)

        qq = input.readIntAt(0)
        val msgLength = input.readShortAt(22)
        val fontLength = input.readShortAt(93 + msgLength)
        val offset = msgLength + fontLength
        message = MessageChain(PlainText(let {
            val offset2 = input.readShortAt(101 + offset)
            input.goto(103 + offset).readVarString(offset2.toInt())
        }))
    }

}

/*


backup

class ServerFriendMessageEventPacket(input: DataInputStream, packetId: ByteArray, eventIdentity: ByteArray) : ServerEventPacket(input, packetId, eventIdentity) {
    var qq: Int = 0
    lateinit var message: String


    @ExperimentalUnsignedTypes
    override fun decode() {
        //start at Sep1.0:27
        qq = input.readIntAt(0)
        val msgLength = input.readShortAt(22)
        val fontLength = input.readShortAt(93+msgLength)
        val offset = msgLength+fontLength
        message = if(input.readByteAt(97+offset).toUHexString() == "02"){
            "[face" + input.goto(103+offset).readByteAt(1).toInt().toString() + ".gif]"
            //.gif
        }else {
            val offset2 = input.readShortAt(101 + offset)
            input.goto(103 + offset).readVarString(offset2.toInt())
        }
    }
}
 */