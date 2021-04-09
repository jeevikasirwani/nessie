/* tslint:disable */
/* eslint-disable */
/**
 * Nessie API
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * The version of the OpenAPI document: 0.5.0
 *
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

import { exists, mapValues } from '../runtime';
/**
 *
 * @export
 * @interface HiveTable
 */
export interface HiveTable {
    /**
     *
     * @type {Array<object>}
     * @memberof HiveTable
     */
    partitions?: Array<object>;
    /**
     *
     * @type {Array<string>}
     * @memberof HiveTable
     */
    tableDefinition?: Array<string>;
}

export function HiveTableFromJSON(json: any): HiveTable {
    return HiveTableFromJSONTyped(json, false);
}

export function HiveTableFromJSONTyped(json: any, ignoreDiscriminator: boolean): HiveTable {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {

        'partitions': !exists(json, 'partitions') ? undefined : json['partitions'],
        'tableDefinition': !exists(json, 'tableDefinition') ? undefined : json['tableDefinition'],
    };
}

export function HiveTableToJSON(value?: HiveTable | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {

        'partitions': value.partitions,
        'tableDefinition': value.tableDefinition,
    };
}
