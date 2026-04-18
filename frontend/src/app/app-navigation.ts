type NavigationItem = { path?: string, text: string, icon?: string, items?: NavigationItem[] };
export const navigation: NavigationItem[] = [
  {
    text: 'Home',
    path: '/home',
    icon: 'home'
  },
  {
    text: 'Data Mgmt',
    icon: 'datafield',
    items: [
      {
        text: 'Candle Data',
        icon: 'chart',
        items: [
          {
            text: 'Download',
            path: '/candle-download'
          },
          {
            text: 'Local Explorer',
            path: '/local-data'
          }
        ]
      },
      {
        text: 'Symbology',
        icon: 'tags',
        items: [
          {
            text: 'Symbol Master',
            path: '/symbol-master'
          }
        ]
      }
    ]
  }
];
