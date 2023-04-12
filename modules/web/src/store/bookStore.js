import { defineStore } from "pinia";
import API from "@api";

export const useBookStore = defineStore("book", {
  state: () => {
    return {
      connectStatus: "正在连接后端服务器……",
      connectType: "",
      newConnect: true,
      searchBooks: [],
      shelf: [],
      catalog: [],
      /**@type {{index: number,chapterPos:number}} */
      readingBook: { index: 0, chapterPos: 0 },
      popCataVisible: false,
      contentLoading: true,
      showContent: false,
      config: {
        theme: 0,
        font: 0,
        fontSize: 18,
        readWidth: 800,
        infiniteLoading: false,
        customFontName: "",
      },
      miniInterface: false,
      readSettingsVisible: false,
    };
  },
  actions: {
    setConnectStatus(connectStatus) {
      this.connectStatus = connectStatus;
    },
    setConnectType(connectType) {
      this.connectType = connectType;
    },
    setNewConnect(newConnect) {
      this.newConnect = newConnect;
    },
    addBooks(books) {
      this.shelf = books;
    },
    setCatalog(catalog) {
      this.catalog = catalog;
    },
    setPopCataVisible(visible) {
      this.popCataVisible = visible;
    },
    setContentLoading(loading) {
      this.contentLoading = loading;
    },
    setReadingBook(readingBook) {
      this.readingBook = readingBook;
    },
    setConfig(config) {
      this.config = config;
    },
    setReadSettingsVisible(visible) {
      this.readSettingsVisible = visible;
    },
    setShowContent(visible) {
      this.showContent = visible;
    },
    setMiniInterface(mini) {
      this.miniInterface = mini;
    },
    async setSearchBooks(books) {
      books.forEach((book) => {
        let findBook = this.shelf.find((item) => item.bookUrl == book.bookUrl);
        if (findBook === undefined) {
          this.searchBooks.push(book);
        }
      });
    },
    clearSearchBooks() {
      this.searchBooks = [];
    },
    //保存进度到app
    async saveBookProcess() {
      if (this.catalog.length == 0) return;
      // @ts-ignore
      const { index, chapterPos, bookName, bookAuthor } = this.readingBook;
      let title = this.catalog[index]?.title;
      if (!title) return;

      API.saveBookProcess({
        name: bookName,
        author: bookAuthor,
        durChapterIndex: index,
        durChapterPos: chapterPos,
        durChapterTime: new Date().getTime(),
        durChapterTitle: title,
      });
    },
  },
});
