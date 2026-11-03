package app.wowforever.addons;

/** Screens shown before the app can work: storage permission and the game folder. */
final class SetupScreen extends Screen {

    SetupScreen(MainActivity app) { super(app); }

    void renderPermission() {
        header("WoW Addons", "Менеджер аддонов для WoW Forever");
        app.bodyBox.addView(emptyState(R.drawable.ic_folder, "Нужен доступ к файлам",
                "Чтобы устанавливать аддоны в папку игры, приложению нужен «Доступ ко всем файлам» — так же, как самому клиенту WoW Forever.",
                Ui.button(app, "Разрешить доступ", R.drawable.ic_check, Ui.Kind.PRIMARY, v -> app.requestStorage())));
    }

    void renderNoGame() {
        header("Мои аддоны", "Папка игры не выбрана");
        app.bodyBox.addView(emptyState(R.drawable.ic_folder, "Где установлен WoW Forever?",
                "Обычно это «WoW Forever» во внутренней памяти или на SD-карте. Выберите папку, в которой лежат .build.info и _forever_.",
                Ui.button(app, "Найти автоматически", R.drawable.ic_search, Ui.Kind.SECONDARY, v -> app.gameFolder.autoDetect(true)),
                Ui.button(app, "Выбрать папку", R.drawable.ic_folder, Ui.Kind.PRIMARY, v -> app.gameFolder.openFolderPicker())));
    }
}
