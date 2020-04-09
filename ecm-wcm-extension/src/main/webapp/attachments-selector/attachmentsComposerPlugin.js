const ActivityComposerAttachmentsPlugin = [
  {
    key: 'file',
    rank: 20,
    resourceBundle: 'locale.attachmentsSelector.attachments',
    labelKey: 'attachments.composer.app.labelKey',
    description: 'attachments.composer.app.description',
    iconClass: 'addFileComposerIcon',
    appClass: 'attachmentsSelector',
    component: {
      name: 'exo-attachments',
      props: {
        showAttachmentsDrawer: false,
        showAttachmentsBackdrop: false,
        maxFilesCount: 20,
        maxFileSize: 25
      },
      model: {
        value: []
      },
      events: [
        {
          'event': 'uploadingFileFinished',
          'listener': 'setUploadingCount',
          'listenerParam': 'add'
        },
        {
          'event': 'removingFileFinished',
          'listener': 'setUploadingCount',
          'listenerParam': 'remove'
        },
        {
          'event': 'attachExistingServerAttachment',
          'listener': 'setUploadingCount',
          'listenerParam': 'addExisting'
        },
        {
          'event': 'attachmentsChanged',
          'listener': 'updateAttachments'
        }
      ],
      show: false
    },
    onExecute: function (attachmentsComponent) {
      attachmentsComponent.showAttachmentsDrawer = true;
    }
  }];

require(['SHARED/extensionRegistry'], function (extensionRegistry) {
  for (const extension of ActivityComposerAttachmentsPlugin) {
    extensionRegistry.registerExtension('ActivityComposer', 'activity-composer-action', extension);
  }
});
