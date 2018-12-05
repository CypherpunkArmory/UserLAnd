package tech.ula.model.state

class SessionStartupFsm {
}

sealed class SessionStartupState
object WaitingForSessionSelection : SessionStartupState()
object SingleSessionSupported : SessionStartupState()
object SessionIsRestartable : SessionStartupState()
object RetrievingRemoteAssetLists : SessionStartupState()
object RetrievingCachedAssetLists: SessionStartupState()
object AssetListsAreUnavailable : SessionStartupState()
object LargeDownloadRequired : SessionStartupState()
object GeneratingDownloadRequirements : SessionStartupState()
object DownloadingRequirements : SessionStartupState()
object DownloadsHaveCompleted : SessionStartupState()
object ExtractingFilesystem : SessionStartupState()
object VerifyingFilesystemAssets : SessionStartupState()
object SessionCanBeActivated : SessionStartupState()

sealed class SessionStartupEvent
object SessionSelected : SessionStartupEvent()
object AssetDownloaded : SessionStartupEvent()