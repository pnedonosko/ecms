<template>
  <v-list-item @click.prevent="openPreview()">
    <v-list-item-icon class="mx-0">
      <v-icon :color="documentIcon.color" x-large>
        {{ documentIcon.ico }}
      </v-icon>
    </v-list-item-icon>
    <v-list-item-content>
      <v-list-item-title v-text="document.title"/>
      <v-list-item-subtitle>
        <div :title="absoluteDateModified()" class="color-title">
          {{ relativeDateModified }}
          <v-icon color="#a8b3c5">
            mdi-menu-right
          </v-icon>
          {{ document.drive }}
        </div>
      </v-list-item-subtitle>
    </v-list-item-content>
  </v-list-item>
</template>
<script>
  export default {
    props: {
      document: {
        type: Object,
        default: () => null,
      }
    },
    computed: {
      downloadUrl() {
        return `/rest/jcr/repository/collaboration${this.document.path}`;
      },
      openUrl() {
        const path = this.document.drive === 'Private' ? 'Personal+Documents' : `.space.${this.document.drive}`;
        return `${eXo.env.portal.context}/${eXo.env.portal.portalName}/documents?path=${path}${this.document.path}`;
      },
      relativeDateModified() {
        return this.getRelativeTime(this.document.dateModified.time);
      },
      documentIcon() {
        const icon = {}
        if (this.document.mimeType.includes('pdf')) {
          icon.ico = 'mdi-file-pdf';
          icon.color = '#d07b7b';
        } else if (this.document.mimeType.includes('mlpresentation')) {
          icon.ico = 'mdi-file-powerpoint';
          icon.color = '#e45030';
        } else if (this.document.mimeType.includes('mlsheet')) {
          icon.ico = 'mdi-file-excel';
          icon.color = '#1a744b';
        } else if (this.document.mimeType.includes('mldocument')) {
          icon.ico = 'mdi-file-word';
          icon.color = '#094d7f';
        } else if (this.document.mimeType.includes('textplain')) {
          icon.ico = 'mdi-clipboard-text';
          icon.color = '#1c9bd7';
        } else if (this.document.mimeType.includes('image')) {
          icon.ico = 'mdi-image';
          icon.color = '#eab320';
        } else {
          icon.ico = 'mdi-file';
          icon.color = '#cdcccc';
        }
        return icon;
      }
    },
    methods: {
      getRelativeTime(previous) {
        const msPerMinute = 60 * 1000;
        const msPerHour = msPerMinute * 60;
        const msPerDay = msPerHour * 24;
        const msPerMaxDays = msPerDay * 2;
        const elapsed = new Date().getTime() - previous;
        if (elapsed < msPerMinute) {
          return this.$t('documents.timeConvert.Less_Than_A_Minute');
        } else if (elapsed === msPerMinute) {
          return this.$t('documents.timeConvert.About_A_Minute');
        } else if (elapsed < msPerHour) {
          return this.$t('documents.timeConvert.About_?_Minutes').replace('{0}', Math.round(elapsed / msPerMinute));
        } else if (elapsed === msPerHour) {
          return this.$t('documents.timeConvert.About_An_Hour');
        } else if (elapsed < msPerDay) {
          return this.$t('documents.timeConvert.About_?_Hours').replace('{0}', Math.round(elapsed / msPerHour));
        } else if (elapsed === msPerDay) {
          return this.$t('documents.timeConvert.About_A_Day');
        } else if (elapsed < msPerMaxDays) {
          return this.$t('documents.timeConvert.About_?_Days').replace('{0}', Math.round(elapsed / msPerDay));
        } else {
          return this.absoluteDateModified({dateStyle: "short"});
        }
      },
      absoluteDateModified(options) {
        const lang = eXo && eXo.env && eXo.env.portal && eXo.env.portal.language || 'en';
        return new Date(this.document.dateModified.time).toLocaleString(lang, options).split("/").join("-");
      },
      openPreview() {
        documentPreview.init({
          doc: {
            id: this.document.id,
            repository: 'repository',
            workspace: 'collaboration',
            path: this.document.path,
            title: this.document.title,
            downloadUrl: this.downloadUrl,
            openUrl: this.openUrl
          },
        });
      }
    }
  }
</script>