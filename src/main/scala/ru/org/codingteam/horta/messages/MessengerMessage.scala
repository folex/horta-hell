package ru.org.codingteam.horta.messages

import org.jivesoftware.smack.{XMPPConnection, Chat}

abstract sealed class MessengerMessage

/**
 * Reconnect request.
 * @param connection connection that was closed.
 */
case class Reconnect(connection: XMPPConnection) extends MessengerMessage

case class JoinRoom(roomJID: String, botName: String, greeting: Option[String]) extends MessengerMessage

case class ChatOpened(chat: Chat) extends MessengerMessage

