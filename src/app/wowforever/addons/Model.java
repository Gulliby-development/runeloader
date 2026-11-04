package app.wowforever.addons;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Plain data types shared by sources, installer and UI. */
final class Model {

    static final String SRC_CATALOG = "github";   // curated catalog entries are GitHub sources
    static final String SRC_GITHUB = "github";
    static final String SRC_WOWI = "wowi";
    static final String SRC_ZIP = "zip";

    /** Something that can be installed: a catalog entry or a search result. */
    static final class Item {
        String source;        // github | wowi
        String id;            // owner/repo (owner/repo#Module for one module) | numeric id
        String name;
        String author = "";
        String summary = "";
        String category = "";
        String categoryKey = "";   // a Categories key, "" when unknown
        List<String> tags = new ArrayList<>();   // index tags such as "gamepad", lower case
        long downloads = -1;
        long updated = 0;     // epoch millis
        String iconUrl;
        String pageUrl;
        String version;       // version string if known from the listing
        boolean foreverVerified;  // the source says it has a Forever build
        List<String> folders = new ArrayList<>();   // known AddOns folders (to recognise manual installs)
        List<String> deps = new ArrayList<>();      // required items, as "source:id"
        String note;          // a Forever caveat to show
        // From the GitHub index (#26-#28); empty when the source has no such data.
        String description = "";
        List<String> screenshots = new ArrayList<>();
        long stars = -1;
        String changelog = "";                        // notes of the latest release
        List<Release> releases = new ArrayList<>();   // newest first

        String key() { return source + ":" + id; }
    }

    /** A concrete downloadable file for an Item. */
    static final class Release {
        String version;
        String url;
        String fileName;
        Map<String, String> headers;
        String md5;
        boolean guessed;      // flavor picked from the file name, not from metadata
        List<Item> deps = new ArrayList<>();
        String changelogUrl;
        // Index metadata, informational only: installs resolve the asset from GitHub directly.
        String tag;
        long released;        // epoch millis
        boolean prerelease;
        long size = -1;
        String changelog = "";
    }

    /** One addon this app installed (persisted). */
    static final class Entry {
        String key;           // source:id
        String source;
        String id;
        String name;
        String author = "";
        String version = "";
        String iconUrl;
        String pageUrl;
        List<String> folders = new ArrayList<>();
        long installedAt;
        boolean pinned;       // the background auto-update skips it
        // runtime only
        String latest;
        boolean updateAvailable;
        String checkError;
        String changelog;     // notes of the latest release, when the index has them
    }

    /** One folder found in Interface/AddOns. */
    static final class LocalAddon {
        String folder;
        String title;
        String version = "";
        String author = "";
        String notes = "";
        String interfaceVersion = "";
        String tocFile = "";
        boolean foreverToc;     // has a _Forever / _Camelot TOC or interface 160xx
        boolean loadOnDemand;
        String wowiId, wagoId, website;
        List<String> deps = new ArrayList<>();
    }

    /** A row of the Installed screen: a managed entry or a group of unmanaged folders. */
    static final class Installed {
        Entry entry;                       // null when installed by hand
        List<LocalAddon> folders = new ArrayList<>();
        String title;
        String version;
        String author;
        String notes;
        boolean compatible = true;
        boolean missing;                   // tracked but folders deleted
        Item catalogMatch;                 // a catalog entry that can manage it

        LocalAddon main() { return folders.isEmpty() ? null : folders.get(0); }
    }

    private Model() {}
}
