// package tech.ula.utils
//
// import android.content.Intent
// import android.net.Uri
// import tech.ula.model.entities.Session
//
// class ClientUtility() {
//
//    fun getClientIntentData(session: Session): String {
//        return when (session.clientType) {
//            "ConnectBot" -> startSshClient(session, "org.connectbot")
//            "bVNC" -> startVncClient(session, "com.iiordanov.freebVNC")
// //            "xsdl" -> return // TODO
//            else -> "clientNotFound"
//        }
//    }
//
//    private fun startSshClient(session: Session, packageName: String): String {
//        val connectBotIntent = Intent()
//        connectBotIntent.action = "android.intent.action.VIEW"
//        connectBotIntent.data = Uri.parse("ssh://${session.username}@localhost:${session.port}/#userland")
//        connectBotIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//
//        if (clientIsPresent(connectBotIntent)) {
//            context.startActivity(connectBotIntent)
//        } else {
//            getClient(packageName)
//        }
//    }
//
//    private fun startVncClient(session: Session, packageName: String): String {
//        val bVncIntent = Intent()
//        bVncIntent.action = "android.intent.action.VIEW"
//        bVncIntent.type = "application/vnd.vnc"
//        bVncIntent.data = Uri.parse("vnc://127.0.0.1:5951/?VncPassword=${session.password}")
//        bVncIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//
//        if (clientIsPresent(bVncIntent)) {
//        } else {
//            getClient(packageName)
//        }
//    }
//
//    private fun getClient(packageName: String) {
//        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
//        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//    }
// }