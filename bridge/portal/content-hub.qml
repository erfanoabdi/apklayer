// Lomiri Content Hub (Ubuntu Touch) for file-chooser.py, which runs it with Qt 5's qmlscene when no FileChooser portal answers. Its lines on
// stderr (QT_MESSAGE_PATTERN=%{message}): `uri <file-uri>` per item, or `cancelled`.
// [multiple] (pick, the default): the source apps for any content (the file manager, the browser's downloads); the item lands in ~/.cache/$APP_ID/HubIncoming/<n>/,
// kept for the caller (no finalize, which would delete it). `import` (argument): the destination side, for the apklayer-share peer: waits for the
// transfer Content Hub hands to $APP_ID (an .apk shared to apklayer from another app) and prints its items.
import QtQuick 2.4
import Lomiri.Components 1.3
import Lomiri.Content 1.3

MainView {
    id: root
    width: units.gu(45)
    height: units.gu(75)
    property bool importing: Qt.application.arguments.indexOf("import") >= 0
    property bool multiple: Qt.application.arguments.indexOf("multiple") >= 0
    property var transfer: null

    function done(t) {
        for (var i = 0; i < t.items.length; i++) console.log("uri " + t.items[i].url);
        Qt.quit();
    }

    ContentPeerPicker {
        visible: !root.importing && root.transfer === null
        showTitle: true
        headerText: "Choose a file from"
        handler: ContentHandler.Source
        contentType: ContentType.All
        onPeerSelected: {
            peer.selectionType = root.multiple ? ContentTransfer.Multiple : ContentTransfer.Single;
            root.transfer = peer.request();
        }
        onCancelPressed: { console.log("cancelled"); Qt.quit(); }
    }

    ContentTransferHint { anchors.fill: parent; activeTransfer: root.transfer }

    Connections {
        target: root.transfer
        function onStateChanged() {
            if (root.transfer.state === ContentTransfer.Charged) root.done(root.transfer);
            else if (root.transfer.state === ContentTransfer.Aborted) { console.log("cancelled"); Qt.quit(); }
        }
    }

    // the service hands a pending transfer over as soon as the handler registers (ContentHub's creation), which can be before this binding exists:
    // finishedImports keeps it
    function imported() {
        var list = ContentHub.finishedImports;
        if (list.length > 0) root.done(list[list.length - 1]);
    }
    Component.onCompleted: if (root.importing) root.imported()
    Connections {
        target: root.importing ? ContentHub : null
        function onFinishedImportsChanged() { root.imported(); }
    }
}
