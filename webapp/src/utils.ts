import { DateTime } from 'luxon';

export function isEmpty(obj: object): boolean {
    for(var key in obj) {
        if(obj.hasOwnProperty(key))
            return false;
    }
    return true;
}

export function formatDateTime(timestamp: any): string {
   var datetime;
   if (!timestamp) {
      return "--"
   } else if (typeof timestamp === "string") {
      datetime = DateTime.fromISO(timestamp)
   } else if (typeof timestamp === "number") {
      datetime = DateTime.fromMillis(timestamp)
   } else {
      return String(datetime)
   }
   return datetime.toFormat("yyyy-LL-dd HH:mm:ss ZZZ")
}

export function toEpochMillis(timestamp: any): number {
   if (!timestamp) {
      return 0
   } else if (typeof timestamp === "string") {
      return DateTime.fromISO(timestamp).toMillis()
   } else if (typeof timestamp === "number") {
      return timestamp
   } else {
      return 0
   }
}
